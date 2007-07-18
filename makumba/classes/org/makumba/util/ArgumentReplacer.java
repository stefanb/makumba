///////////////////////////////
//  Makumba, Makumba tag library
//  Copyright (C) 2000-2003  http://www.makumba.org
//
//  This library is free software; you can redistribute it and/or
//  modify it under the terms of the GNU Lesser General Public
//  License as published by the Free Software Foundation; either
//  version 2.1 of the License, or (at your option) any later version.
//
//  This library is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
//  Lesser General Public License for more details.
//
//  You should have received a copy of the GNU Lesser General Public
//  License along with this library; if not, write to the Free Software
//  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
//
//  -------------
//  $Id$
//  $Name$
/////////////////////////////////////

package org.makumba.util;

import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

/**
 * Parses a string and identifies the arguments, to allow operations with them for now, arguments are of the form
 * $javaid[$] but the class can be extended for them to take other forms
 * 
 * @author Cristian Bogdan
 * @version $Id$
 */
public class ArgumentReplacer {
    Vector text = new Vector();

    Dictionary argumentNames = new Hashtable();

    Vector argumentOrder = new Vector();

    /** Gets the arguments list 
     *  @return An Enumeration containing the list of arguments
     */
    public Enumeration getArgumentNames() {
        return argumentNames.keys();
    }

    /**
     * Replaces the arguments in a dictionary by their equivalent in numbers
     * @param d the dictionary containing the arguments in their original form
     * @return A String with the respective values replaced
     *  */
    public String replaceValues(Dictionary d) {
        StringBuffer sb = new StringBuffer();
        Enumeration f = argumentOrder.elements();
        Enumeration e = text.elements();
        while (true) {
            sb.append(e.nextElement());
            if (f.hasMoreElements()) {
                Object nm = f.nextElement();
                Object o = d.get(nm);
                if (o == null)
                    throw new RuntimeException(nm + " " + d);
                sb.append(o);
            } else
                break;
        }
        return sb.toString();
    }

    /**
     * Makes a list of all arguments and where they are
     * @param s the string containing the arguments
     */
    public ArgumentReplacer(String s) {
        int dollar;
        String prev = "";
        boolean doubledollar;
        int n;
        String argname;

        while (true) {
            dollar = s.indexOf('$');
            if (dollar == -1 || s.length() == dollar + 1) {
                text.addElement(prev + s);
                break;
            }

            dollar++;
            if ((doubledollar = s.charAt(dollar) == '$') || !Character.isJavaIdentifierStart(s.charAt(dollar))) {
                prev = s.substring(0, dollar);
                if (doubledollar)
                    dollar++;
                if (s.length() > dollar) {
                    s = s.substring(dollar);
                    continue;
                } else {
                    text.addElement(prev);
                    break;
                }
            }
            text.addElement(prev + s.substring(0, dollar - 1));
            prev = "";

            for (n = dollar + 1; n < s.length() && s.charAt(n) != '$' && Character.isJavaIdentifierPart(s.charAt(n)); n++)
                ;
            argname = s.substring(dollar, n);
            if (n < s.length() && s.charAt(n) == '$')
                n++;
            argumentNames.put(argname, "");
            argumentOrder.addElement(argname);
            if (n < s.length()) {
                s = s.substring(n);
                continue;
            } else {
                text.addElement("");
                break;
            }
        }
    }
}
