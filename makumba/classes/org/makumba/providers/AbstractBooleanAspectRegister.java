package org.makumba.providers;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Collection;

import org.apache.commons.collections.map.MultiValueMap;
import org.makumba.MakumbaError;
import org.makumba.commons.AnnotationUtil;

/**
 * Registry for simple boolean aspects. Makes it possible to plug-in in matching conditions based on a return type,
 * annotation, and a property thereof. For the moment only supports single values, let's see how this performs first.
 * 
 * @author manu
 * @version $Id: AbstractAspectRegister.java,v 1.1 Jun 9, 2010 12:58:13 PM manu Exp $
 */
public abstract class AbstractBooleanAspectRegister {

    private final static Class<?>[] emptyClassArray = new Class<?>[] {};

    private final static Object[] emptyObjectArray = new Object[] {};

    protected MultiValueMap aspects = new MultiValueMap();

    protected void registerAspect(String name, Class<?> type, Class<? extends Annotation> annotation,
            String annotationPropertyName, Object annotationPropertyValue) {
        aspects.put(name, new Aspect(name, type, annotation, annotationPropertyName, annotationPropertyValue));
    }

    protected void registerAspect(String name, Class<?> type, Class<? extends Annotation> annotation) {
        aspects.put(name, new Aspect(name, type, annotation, null, null));
    }

    /** register your matching aspects here **/
    public abstract void registerAspects();

    protected boolean match(String aspect, Member m) {
        Collection<Aspect> as = aspects.getCollection(aspect);
        if (as == null) {
            throw new MakumbaError("Aspect '" + aspect + "' not registered");
        }

        boolean matches = false;

        for (Aspect a : as) {

            // if not any return type
            if (a.getType() != null) {
                matches = m.getDeclaringClass().getName().equals(a.getType().getName());
                if (!matches) {
                    continue;
                }
            }

            if (m instanceof Method) {
                Method me = (Method) m;
                Class<? extends Annotation> annotationClass = a.getAnnotationClass();
                matches = me.isAnnotationPresent(annotationClass);
                if (!matches) {
                    continue;
                }
                if (a.getAnnotationPropertyName() == null) {
                    return matches;
                }

                Object v = AnnotationUtil.readAttributeValue(me, annotationClass, a.getAnnotationPropertyName());
                matches = v.equals(a.getAnnotationPropertyValue());
                if (!matches) {
                    continue;
                } else {
                    return matches;
                }

            } else if (m instanceof Field) {
                // TODO
                throw new MakumbaError("matching not implemented for fields");
            }

        }

        return matches;

    }

    class Aspect {

        private String name;

        private Class<?> type;

        private Class<? extends Annotation> annotationClass;

        private String annotationPropertyName;

        private Object annotationPropertyValue;

        public String getName() {
            return name;
        }

        public Class<?> getType() {
            return type;
        }

        public Class<? extends Annotation> getAnnotationClass() {
            return annotationClass;
        }

        public String getAnnotationPropertyName() {
            return annotationPropertyName;
        }

        public Object getAnnotationPropertyValue() {
            return annotationPropertyValue;
        }

        public Aspect(String name, Class<?> type, Class<? extends Annotation> annotationClass,
                String annotationPropertyName, Object annotationPropertyValue) {
            super();
            this.name = name;
            this.type = type;
            this.annotationClass = annotationClass;
            this.annotationPropertyName = annotationPropertyName;
            this.annotationPropertyValue = annotationPropertyValue;
        }
    }

    public static void main(String... args) throws Exception {
        // ClassReader r = new ClassReader("test.Person");
        // StringWriter sw = new StringWriter();
        // String[] arg = new String[] {
        // "/home/manu/Dropbox/workspace/makumba/webapps/tests/WEB-INF/classes/test/Person.class" };
        // TraceClassVisitor.main(arg);

    }

}
