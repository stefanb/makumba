// TODO
// add !include mechanism (from beginning in parser)

// other todo:
//   take care of validation
//   take care of functions

header {
    package org.makumba.providers.datadefinition.mdd;
    
    import java.net.URL;
    import org.apache.commons.collections.map.ListOrderedMap;
}

class MDDAnalyzeBaseWalker extends TreeParser;

options {
    importVocab=MDD;
    buildAST=true;
    k=1;
}

{
    RecognitionException error;
    
    public void reportError(RecognitionException e) {
        error=e;
    }

    public void reportError(String s) {
        if (error == null)
            error = new RecognitionException(s);
    }
    
    protected String typeName;
    
    protected URL origin;
    
    protected MDDNode mdd;
    
    private FieldNode currentField;
    
    // ordered map to keep track of fields and handle duplicates, i.e. overriden fields
    private ListOrderedMap fields = new ListOrderedMap();
    
    // set currently analyzed field
    protected void setCurrentField(FieldNode field) { this.currentField = field; }
    
    // get currently analyzed field
    protected FieldNode getCurrentField() { if(this.currentField == null) this.currentField = new FieldNode(mdd, "dummy"); return this.currentField; }
    
    // set makumba type of currently analyzed field
    protected void setCurrentFieldType(FieldType type) { if(this.currentField != null) this.currentField.makumbaType = type; }

    // set type of currently analyzed field if mak type is unknown
    protected void setCurrentFieldTypeUnknown(String type) { if(this.currentField != null) this.currentField.unknownType = type; }
    
    // Check if type and type attributes are correct
    protected void checkFieldType(AST type) { }
    
    // Check if subfield type is allowed - same as field type but without ptrOne and setComplex
    protected void checkSubFieldType(AST type) { }
    
    // Check if name of parent in subfield is the name of parent
    protected void checkSubFieldName(AST parentName, AST name) { }
    
    // Add type shorthand
    protected void addTypeShorthand(AST name, AST fieldType) { }
    
    // Add modifier
    protected void addModifier(FieldNode field, String modifier) { }
    
    // Add subfield - setComplex, ptrOne
    protected void addSubfield(FieldNode field) { }
        
}

dataDefinition
    : (declaration)*
    ;

declaration
    : fieldDeclaration
    | t:titleDeclaration { mdd.setTitleField((TitleFieldNode) #t); }
    | typeDeclaration
    ;

fieldDeclaration
    : #(
            f:FIELD
            fn:FIELDNAME { FieldNode field = new FieldNode(mdd, #fn.getText(), #f); setCurrentField(field); }
              (m:MODIFIER { addModifier(field, #m.getText()); })*
              ft:fieldType { checkFieldType(#ft); }
              (fc:FIELDCOMMENT { getCurrentField().description = #fc.getText(); } )?
                ( { MDDNode subFieldDD = field.initSubfield(); }
                  #(
                      sf:SUBFIELD
                      PARENTFIELDNAME { checkSubFieldName(#fn, #PARENTFIELDNAME); }
                      (
                          (t:titleDeclaration { subFieldDD.setTitleField((TitleFieldNode) #t); field.addChild(#t); }) // FIXME add child to tree so it can be processed in builder
                          |
                          (
                              sfn:SUBFIELDNAME { FieldNode subField = new FieldNode(subFieldDD, #sfn.getText(), #sf); setCurrentField(subField); }
                              (sm:MODIFIER { addModifier(subField, #sm.getText());} )*
                              sft:fieldType { checkSubFieldType(#sft); }
                              (sfc:FIELDCOMMENT { subField.description = #sfc.getText(); })?
                              {
                                  // we add the subField to the field
                                  field.addSubfield(subField);
                                  field.addChild(subField);
                              }
                          )
                      )
                   )
                )* {
                      // we set back the current field
                      setCurrentField(field);
                   }
            
       ) {
                mdd.addField(field);
                            
                // in the end, the return tree contains only one FieldNode
                #fieldDeclaration = field;
                
                // handle overriden fields
                if(fields.containsKey(#fn.getText())) {
                  // fetch previous field, replace sibling with next
                  int i = fields.indexOf(#fn.getText());
                  AST previous = (AST) fields.getValue(i-1);
                  AST next = null;
                  if(fields.size() <= i+1) {
                    next = #field;
                  } else {
                    next = (AST)fields.getValue(i+1);
                  }
                  previous.setNextSibling(next);
                }
                fields.put(#fn.getText(), #fieldDeclaration);
            
                
         }
    ;
    
    
fieldType
    :
    { FieldType type = null; } (
      u:UNKNOWN_TYPE { setCurrentFieldTypeUnknown(#u.getText()); } // will need processing afterwards, this happens when dealing with macro types - needs to be stored somehow in the field though!
    | #(CHAR { type = FieldType.CHAR; }
        cl:CHAR_LENGTH { getCurrentField().charLength = Integer.parseInt(#cl.getText()); }
       )
    | INT { type = FieldType.INT; }
    | #(
        INTENUM { type = FieldType.INTENUM; } ( { boolean isDeprecated = false; }
                 it:INTENUMTEXT
                 ii:INTENUMINDEX
                 (id:DEPRECATED { isDeprecated = true; } )?
                 {
                    if(isDeprecated) {
                        getCurrentField().addIntEnumValueDeprecated(Integer.parseInt(#ii.getText()), #it.getText());
                    } else {
                        getCurrentField().addIntEnumValue(Integer.parseInt(#ii.getText()), #it.getText());
                    }
                 }
                )*
        )
                
    | REAL { type = FieldType.REAL; }
    | BOOLEAN { type = FieldType.BOOLEAN; }
    | TEXT { type = FieldType.TEXT; }
    | BINARY { type = FieldType.BINARY; }
    | FILE { type = FieldType.FILE; }
    | DATE { type = FieldType.DATE; }
    | #(PTR { type = FieldType.PTRONE; #fieldType.setType(PTRONE); } (p:POINTED_TYPE { getCurrentField().pointedType = #p.getText(); type =FieldType.PTR; })? )
    | #(SET { type = FieldType.SETCOMPLEX; #fieldType.setType(SETCOMPLEX); } (s:POINTED_TYPE { getCurrentField().pointedType = #s.getText(); type = FieldType.SET; })? )
    )
    {
        setCurrentFieldType(type);
        ((MDDAST)#fieldType).makumbaType = type;
    }
    ;
    
titleDeclaration
    : tf:TITLEFIELDFIELD { #tf.setType(TITLEFIELD); ((TitleFieldNode)#tf).titleType = FIELD; }
    | tfun:TITLEFIELDFUNCTION { #tfun.setType(TITLEFIELD); ((TitleFieldNode)#tfun).titleType = FUNCTION; }
    ;

typeDeclaration! // we kick out the declaration after registering it
    : name:TYPENAME ft:fieldType { checkFieldType(#ft); addTypeShorthand(#name, #ft); }
    ;