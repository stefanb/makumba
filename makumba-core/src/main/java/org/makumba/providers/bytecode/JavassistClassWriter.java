package org.makumba.providers.bytecode;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Vector;

import javassist.CannotCompileException;
import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import javassist.NotFoundException;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.AttributeInfo;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.AnnotationMemberValue;
import javassist.bytecode.annotation.ArrayMemberValue;
import javassist.bytecode.annotation.BooleanMemberValue;
import javassist.bytecode.annotation.ClassMemberValue;
import javassist.bytecode.annotation.EnumMemberValue;
import javassist.bytecode.annotation.IntegerMemberValue;
import javassist.bytecode.annotation.MemberValue;
import javassist.bytecode.annotation.StringMemberValue;

import javax.persistence.ManyToMany;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.makumba.MakumbaError;
import org.makumba.commons.NameResolver;

/**
 * TODO optimize memory consumption if possible, read {@link ClassPool} documentation<br>
 * 
 * @author Manuel Bernhardt (manuel@makumba.org)
 * @version $Id: JavassistClassWriter.java,v 1.1 Jun 18, 2010 4:12:07 PM manu Exp $
 */
public class JavassistClassWriter extends AbstractClassWriter {

    @Override
    public void addField(Clazz clazz, String name, String type) {

        CtClass cc = (CtClass) clazz.getClassObjectReference();
        try {
            addField(name, type, cc);
        } catch (CannotCompileException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void addClassAnnotations(Clazz clazz, Vector<AbstractAnnotation> annotations) {
        CtClass cc = (CtClass) clazz.getClassObjectReference();
        ClassFile cf = cc.getClassFile();
        cf.addAttribute(constructAnnotationAttributeInfo(clazz, annotations));
        cf.setVersionToJava5();
    }

    @Override
    public void addMethodAnnotations(Clazz clazz, String methodName, Vector<AbstractAnnotation> annotations) {
        CtMethod m = null;
        CtClass cc = (CtClass) clazz.getClassObjectReference();
        try {
            m = cc.getDeclaredMethod(methodName);
        } catch (NotFoundException e) {
            throw new MakumbaError("Method " + methodName + " not found in class " + clazz.getName());
        }

        m.getMethodInfo().addAttribute(constructAnnotationAttributeInfo(clazz, annotations));
    }

    private AttributeInfo constructAnnotationAttributeInfo(Clazz clazz, Vector<AbstractAnnotation> annotations) {
        CtClass cc = (CtClass) clazz.getClassObjectReference();
        ClassFile cf = cc.getClassFile();
        ConstPool cp = cf.getConstPool();

        AnnotationsAttribute attr = new AnnotationsAttribute(cp, AnnotationsAttribute.visibleTag);

        for (AbstractAnnotation aa : annotations) {
            @SuppressWarnings("unchecked")
            Map<String, Object> attribues = aa.getAttribues();
            Annotation a = addAnnotation(aa.getName(), cp, attribues);
            if (attr.getAnnotations().length > 0) {
                Annotation[] anns = (Annotation[]) ArrayUtils.add(attr.getAnnotations(), a);
                attr.setAnnotations(anns);
            } else {
                attr.setAnnotation(a);
            }
        }
        return attr;
    }

    private Annotation addAnnotation(String annotationName, ConstPool cp, Map<String, Object> annotationAttributes) {
        Annotation a = new Annotation(annotationName, cp);
        for (String attribute : annotationAttributes.keySet()) {
            Object v = annotationAttributes.get(attribute);
            MemberValue mv = getMemberValue(cp, v);
            a.addMemberValue(attribute, mv);
        }
        return a;
    }

    private MemberValue getMemberValue(ConstPool cp, Object v) throws MakumbaError {
        MemberValue mv = null;
        if (v instanceof String) {
            mv = new StringMemberValue((String) v, cp);
        } else if (v instanceof AbstractAnnotation) {
            // nested annotations, oh joy!
            AbstractAnnotation nestedAnnotation = (AbstractAnnotation) v;
            AnnotationMemberValue amv = new AnnotationMemberValue(cp);
            @SuppressWarnings("unchecked")
            Map<String, Object> attribues = nestedAnnotation.getAttribues();
            Annotation na = addAnnotation(nestedAnnotation.getName(), cp, attribues);
            amv.setValue(na);
            mv = amv;
        } else if (v instanceof Enum<?>) {
            EnumMemberValue emv = new EnumMemberValue(cp);
            emv.setType(((Enum<?>) v).getClass().getName());
            emv.setValue(((Enum<?>) v).name());
            mv = emv;
        } else if (v instanceof Class<?>) {
            ClassMemberValue cmv = new ClassMemberValue(cp);
            cmv.setValue(((Class<?>) v).getName());
            mv = cmv;
        } else if (v instanceof Boolean) {
            BooleanMemberValue bmv = new BooleanMemberValue(cp);
            bmv.setValue((Boolean) v);
            mv = bmv;
        } else if (v instanceof Integer) {
            IntegerMemberValue imv = new IntegerMemberValue(cp);
            imv.setValue((Integer) v);
            mv = imv;
        } else if (v instanceof Collection<?>) {
            Collection<?> c = (Collection<?>) v;
            // multi-value map ends up putting everything into a collection so we see if we
            // really need an array here or not
            if (c.size() == 1) {
                mv = getMemberValue(cp, c.iterator().next());
            } else {
                ArrayMemberValue amv = new ArrayMemberValue(cp);
                Vector<MemberValue> r = new Vector<MemberValue>();
                for (Object o : c) {
                    MemberValue omv = getMemberValue(cp, o);
                    r.add(omv);
                }
                amv.setValue(r.toArray(new MemberValue[] {}));
                mv = amv;
            }
        } else {
            throw new MakumbaError("Error while trying to construct annotation: unhandled type "
                    + v.getClass().getName());
        }
        return mv;
    }

    @Override
    public void appendField(String fullyQualifiedClassName, String fieldName, String type, String generatedClassPath) {
        ClassPool cp = ClassPool.getDefault();
        cp.insertClassPath(new ClassClassPath(this.getClass()));
        CtClass cc;

        try {
            cc = cp.get(fullyQualifiedClassName);
            cc.defrost();

            // remove the field if it already exists
            try {
                cc.getDeclaredMethod("get" + StringUtils.capitalize(fieldName));
                removeField(fieldName, type, cc);
            } catch (NotFoundException nfe) {
                // ignore
            }
            addField(fieldName, type, cc);
            cc.writeFile(generatedClassPath);

        } catch (NotFoundException e) {
            e.printStackTrace();
        } catch (CannotCompileException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void addField(String name, String type, CtClass cc) throws CannotCompileException {
        String fieldName = NameResolver.checkReserved(name);
        cc.addField(CtField.make("private " + type + " " + fieldName + ";", cc));
        cc.addMethod(CtNewMethod.getter("get" + StringUtils.capitalize(name),
            CtField.make("private " + type + " " + fieldName + ";", cc)));
        cc.addMethod(CtNewMethod.setter("set" + StringUtils.capitalize(name),
            CtField.make("private " + type + " " + fieldName + ";", cc)));
    }

    private void removeField(String name, String type, CtClass cc) throws CannotCompileException {
        String fieldName = NameResolver.checkReserved(name);
        try {
            cc.removeMethod(cc.getDeclaredMethod("get" + StringUtils.capitalize(name)));
            cc.removeMethod(cc.getDeclaredMethod("set" + StringUtils.capitalize(name)));
            cc.removeField(cc.getField(fieldName));
        } catch (NotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void appendAnnotations(String fullyQualifiedClassName, String methodName,
            Vector<AbstractAnnotation> annotations, String generatedClassPath) {
        ClassPool cp = ClassPool.getDefault();
        cp.insertClassPath(new ClassClassPath(this.getClass()));
        CtClass cc;

        try {
            cc = cp.get(fullyQualifiedClassName);
            cc.defrost();

            Clazz clazz = new Clazz(fullyQualifiedClassName);
            clazz.setClassObjectReference(cc);
            addMethodAnnotations(clazz, methodName, annotations);
            cc.getClassFile().setVersionToJava5();

            cc.writeFile(generatedClassPath);

        } catch (NotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (CannotCompileException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    @Override
    public Clazz createClass(String fullyQualifiedName) {

        ClassPool cp = ClassPool.getDefault();
        cp.insertClassPath(new ClassClassPath(this.getClass()));
        CtClass cc = cp.makeClass(fullyQualifiedName);
        cc.stopPruning(true);

        try {
            cc.addConstructor(CtNewConstructor.make("public " + getClassName(fullyQualifiedName) + "() { }", cc));
        } catch (CannotCompileException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        Clazz clazz = new Clazz(fullyQualifiedName);
        clazz.setClassObjectReference(cc);
        return clazz;

    }

    @Override
    public void writeClass(Clazz clazz, String generatedClassPath) {

        try {
            CtClass cc = (CtClass) clazz.getClassObjectReference();
            // ClassFileWriter.print(cc.getClassFile());
            cc.writeFile(generatedClassPath);

        } catch (CannotCompileException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static void main(String argv[]) throws Exception {

        ClassPool cp = ClassPool.getDefault();
        cp.insertClassPath(new ClassClassPath(EntityClassGenerator.class));
        CtClass cc = cp.makeClass("A");
        cc.stopPruning(true);
        // String type = null;
        CtField fld = CtField.make("public java.util.List myField;", cc);

        ClassFile cf = cc.getClassFile();
        ConstPool cop = cf.getConstPool();

        AnnotationsAttribute attr = new AnnotationsAttribute(cop, AnnotationsAttribute.visibleTag);
        Annotation anno = new Annotation("javax.persistence.ManyToMany", cop);
        anno.addMemberValue("targetEntity", new ClassMemberValue("java.lang.String", cop));
        attr.setAnnotation(anno);
        fld.getFieldInfo().addAttribute(attr);
        cf.setVersionToJava5();

        cc.addField(fld);
        cc.writeFile("build");

        Class<?> A = Class.forName("A");
        System.out.println(A.getField("myField").getAnnotation(ManyToMany.class));

    }

}
