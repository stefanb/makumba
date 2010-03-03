package org.makumba.providers.datadefinition.mdd;

import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Vector;

import org.makumba.DataDefinitionNotFoundError;
import org.makumba.commons.ReservedKeywords;
import org.makumba.providers.QueryAnalysis;
import org.makumba.providers.QueryProvider;
import org.makumba.providers.TransactionProvider;
import org.makumba.providers.query.mql.FunctionInliner;
import org.makumba.providers.query.mql.HqlParser;
import org.makumba.providers.query.mql.MqlQueryAnalysis;
import org.makumba.providers.query.mql.MqlQueryAnalysisProvider;
import org.makumba.providers.query.mql.Node;

import antlr.RecognitionException;
import antlr.TokenStream;
import antlr.TokenStreamException;
import antlr.collections.AST;

/**
 * MDD Parser extending the parser generated by ANTLR for performing specific pre-processing operations
 * @author Manuel Gay
 * 
 * @version $Id: MDDParser.java,v 1.1 May 12, 2009 11:37:36 AM manu Exp $
 */
public class MDDParser extends MDDBaseParser {

    private MDDFactory factory = null;
    
    public MDDParser(TokenStream lexer, MDDFactory factory, String typeName, boolean included) {
        super(lexer);
        this.factory = factory;
        this.typeName = typeName;
        this.included = included;
    }
    
    
    protected Vector<String> includedFieldNames = new Vector<String>();
    
    protected HashMap<AST, AST> parsedFunctions = new HashMap<AST, AST>();
    
    @Override
    protected AST include(AST type) {
        AST included = null;
        try {
            included = factory.parseIncludedDataDefinition(type.getText()).getAST();
        } catch(DataDefinitionNotFoundError e) {
            factory.doThrow(type.getText(), "Could not find included data definition", type);
        }
        
        return included;
        
    }
    
    @Override
    protected AST includeSubField(AST type, AST parentField) {
        AST included = include(type);
        AST transformed = transformToSubfield(parentField, included);
        return transformed;
    }
    
    private AST transformToSubfield(AST parentField, AST included) {
        // process the included AST so that it fits the subField structure parentName -> field = type
        AST t = included;
        AST result = null;
        
        while(t != null) {
            
            //  subfield1
            //  |
            //  |-- parentFieldName
            //  |   |
            //  |   titleField
            //  |
            //  subfield2
            //  |
            //  |-- parentFieldName
            //  |   |
            //  |   subFieldName
            //  |   |
            //  |   subFieldType
            //  |
            //  subfield3
            //  ...
            MDDAST subfield = new MDDAST();
            subfield.setText("->!");
            subfield.setType(MDDTokenTypes.SUBFIELD);
            subfield.setLine(parentField.getLine());
            subfield.wasIncluded = true;
            
            MDDAST parentFieldName = new MDDAST();
            parentFieldName.setText(parentField.getText());
            parentFieldName.setType(MDDTokenTypes.PARENTFIELDNAME);
            parentFieldName.setLine(parentField.getLine());
            parentFieldName.wasIncluded = true;
            
            subfield.setFirstChild(parentFieldName);
            
            // build tree
            if(t.getType() == MDDTokenTypes.TITLEFIELDFIELD || t.getType() == MDDTokenTypes.TITLEFIELDFUNCTION) {
                subfield.getFirstChild().setNextSibling(t);
            } else {
                t.getFirstChild().setType(MDDTokenTypes.SUBFIELDNAME);
                includedFieldNames.add(t.getText());
                subfield.getFirstChild().setNextSibling(t.getFirstChild());
            }
            
            
            if(result == null) {
                result = subfield;
            } else {
                getLastSibling(result).setNextSibling(subfield);
            }
           
           t = t.getNextSibling();
        }
        return result;
    }
    
    private AST getLastSibling(AST t) {
        while(t.getNextSibling() != null) {
            t = t.getNextSibling();
        }
        return t;
    }
    
    private HashMap<String, AST> fieldsToDisable = new HashMap<String, AST>();
    
    @Override
    protected void disableField(AST field) {
        fieldsToDisable.put(field.getText(), field);
    }
    
    /**
     * performs post-processing tasks of parsing:<br>
     * - deactivates disabled fields from inclusions<br>
     */
    protected void postProcess() {
        AST t = getAST();
        AST prec = null;
        while(t!= null && t.getNextSibling() != null) {
            prec = t;
            t = t.getNextSibling();
            if(t.getType() == MDDTokenTypes.FIELD && fieldsToDisable.containsKey(t.getText())) {
                prec.setNextSibling(t.getNextSibling());
                fieldsToDisable.remove(t.getText());
            }
        }
        
        for(String key : fieldsToDisable.keySet()) {
            factory.doThrow(this.typeName, "Fields cannot have an empty body unless they override an included field", fieldsToDisable.get(key));
        }
    }
    
    protected AST parseExpression(AST expression) {
            
            Reader in = new StringReader(expression.getText());
            MDDLexer lexer = new MDDLexer(in);
            MDDExpressionParser parser = new MDDExpressionParser(lexer, factory, typeName, expression);
            parser.setASTNodeClass("org.makumba.providers.datadefinition.mdd.MDDAST");
            try {
                parser.expression();
            } catch (RecognitionException e) {
                e.column = expression.getColumn() + e.column;
                e.line = expression.getLine();
                factory.doThrow(e, expression, typeName);
            } catch (TokenStreamException e) {
                factory.doThrow(e, expression, typeName);
            }   
            if(parser.error != null) {
                RecognitionException e = (RecognitionException) parser.error;
                e.column = expression.getColumn() + e.column;
                e.line = expression.getLine();
                factory.doThrow(e, expression, typeName);
            }
            
            AST tree = parser.getAST();
            if(tree != null)
                shift(tree, expression);
            
            
//            System.out.println("/////////////// Expression parser");
//            MakumbaDumpASTVisitor visitor = new MakumbaDumpASTVisitor(false);
//            visitor.visit(parser.getAST());
            
            return parser.getAST();
    }
    
    @Override
    protected AST parseFunctionBody(AST expression) {
        
        // here we parse the function to see if it's okay
        // when the expression is a subquery, i.e. starts with SELECT, we add parenthesis around it
        boolean subquery = expression.getText().toUpperCase().startsWith("SELECT ");
        
        int offset = "SELECT ".length();
        if(subquery) {
            offset += 1;
        }
        
        String query = "SELECT " + (subquery?"(":"") + expression.getText() + (subquery?")":"") + " FROM " + typeName + " makumbaGeneratedAlias";
        HqlParser parser = HqlParser.getInstance(query);
        try {
            parser.statement();
        } catch (RecognitionException e) {
            e.column = expression.getColumn() + e.column;
            e.line = expression.getLine();
            factory.doThrow(e, expression, typeName);
        } catch (TokenStreamException e) {
            factory.doThrow(e, expression, typeName);
        }   
        
        if(parser.getError() != null) {
            if(parser.getError() instanceof RecognitionException) {
                RecognitionException e = (RecognitionException) parser.getError();
                e.column = expression.getColumn() + e.column -offset;
                e.line = expression.getLine();
                factory.doThrow(e, expression, typeName);
            }
        }
        
        AST tree = parser.getAST();
        if(tree != null)
            shiftHql(tree, expression, offset);
        
        /* FIXME we can't do this here because then we want to access the MDD from within the inliner and it doesn't exist yet
        // now that we did the parsing, we also try to inline the AST
        // that way we will get the errors thrown by the inlining here and can display them nicely
        FunctionInliner inliner = new FunctionInliner(query);
        
        try {
            inliner.inlineFunctions(parser.getAST(), true);
        } catch(Throwable t) {
            factory.doThrow(typeName, t.getMessage(), parser.getAST());
        }
        */
        
        return parser.getAST();
        
    }
    
    private void shift(AST toShift, AST parent) {
        ((MDDAST)toShift).setLine(parent.getLine());
        ((MDDAST)toShift).setCol(parent.getColumn() + toShift.getColumn());
        
        if(toShift.getNextSibling() != null)
            shift(toShift.getNextSibling(), parent);
        if(toShift.getFirstChild() != null)
            shift(toShift.getFirstChild(), parent);
    }
    
    private void shiftHql(AST toShift, AST parent, int offset) {
        ((Node)toShift).setLine(parent.getLine());
        ((Node)toShift).setCol(parent.getColumn() + toShift.getColumn() + offset);
        
        if(toShift.getNextSibling() != null)
            shiftHql(toShift.getNextSibling(), parent, offset);
        if(toShift.getFirstChild() != null)
            shiftHql(toShift.getFirstChild(), parent, offset);
    }

    
    @Override
    protected void errorNestedSubfield(AST s) {
        factory.doThrow(typeName, "Nested subtypes are not allowed", s);
    }
    
    @Override
    protected void checkFieldName(AST fieldName) {
        
        String nm = fieldName.getText();
        
        for (int i = 0; i < nm.length(); i++) {
            if (i == 0 && !Character.isJavaIdentifierStart(nm.charAt(i)) || i > 0
                    && !Character.isJavaIdentifierPart(nm.charAt(i))) {
                factory.doThrow(this.typeName, "Invalid character \"" + nm.charAt(i) + "\" in field name \"" + nm, fieldName);
            }
        }

        if (ReservedKeywords.isReservedKeyword(nm)) {
            factory.doThrow(this.typeName, "Error: field name cannot be one of the reserved keywords "
                    + ReservedKeywords.getKeywordsAsString(), fieldName);
        }
    }
    
    @Override
    protected void addParsedFunction(AST a, AST b) {
        parsedFunctions.put(a, b);
    }
    
 
}
