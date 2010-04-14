package test;

import java.util.ArrayList;
import java.util.Date;
import java.util.Dictionary;
import java.util.GregorianCalendar;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;

import org.makumba.Pointer;
import org.makumba.Text;
import org.makumba.Transaction;
import org.makumba.providers.TransactionProvider;

import com.meterware.httpunit.HttpUnitOptions;

/**
 * Class that creates and deletes data in the database, used by the test set-up.
 * 
 * @author Manuel Gay
 * @version $Id: MakumbaTestData.java,v 1.1 Jun 9, 2009 10:46:01 PM manu Exp $
 */
public class MakumbaTestData {

    public static final String namePersonIndivName_Bart = "bart";

    public static final String namePersonIndivSurname_Bart = "van Vandervanden";

    public static final String namePersonIndivName_John = "john";

    public static final String namePersonIndivSurname_John = "von Neumann";

    private static ArrayList<Pointer> languages = new ArrayList<Pointer>();

    private static String[][] languageData = { { "English", "en" }, { "French", "fr" }, { "German", "de" },
            { "Italian", "it" }, { "Spanish", "sp" } };

    public static Date birthdateJohn;

    public static Date birthdateBart;

    public static Date testDate;

    public static final Integer uniqInt = new Integer(255);

    public static final String uniqChar = new String("testing \" character field");

    public final static String namePersonIndivName_AddToNew = "addToNewPerson";

    public static final String namePersonIndivName_FirstBrother = "firstBrother";

    public static final String namePersonIndivName_SecondBrother = "secondBrother";

    public static final String namePersonIndivName_StepBrother = "stepBrother";

    /** All names of individuals to be deleted. bart is referenced by john, so we delete him afterwards. */
    private static final String[] namesPersonIndivName = { namePersonIndivName_John, namePersonIndivName_Bart,
            namePersonIndivName_AddToNew, namePersonIndivName_SecondBrother, namePersonIndivName_FirstBrother,
            namePersonIndivName_StepBrother };

    static {
        birthdateJohn = new GregorianCalendar(1977, 2, 5, 0, 0, 0).getTime();
        birthdateBart = new GregorianCalendar(1982, 5, 7, 0, 0, 0).getTime();
        testDate = new GregorianCalendar(2008, 2, 9, 0, 0, 0).getTime();

        // because there is a bug in rhino.jar with DOM, HttpUnit throws an exception when going over a specific kind of
        // JS generated by mak
        // here we deactivate this kind of exception throwing
        // http://saloon.javaranch.com/cgi-bin/ubb/ultimatebb.cgi?ubb=get_topic&f=68&t=000747
        HttpUnitOptions.setExceptionsThrownOnScriptError(false);

    }

    public void insertPerson(Transaction t) {
        Hashtable<String, Object> p = new Hashtable<String, Object>();

        p.put("indiv.name", namePersonIndivName_Bart);
        p.put("indiv.surname", namePersonIndivSurname_Bart);
        p.put("birthdate", birthdateBart);
        p.put("gender", new Integer(1));
        Pointer brother = t.insert("test.Person", p);

        p.clear();
        p.put("indiv.name", namePersonIndivName_John);
        p.put("indiv.surname", namePersonIndivSurname_John);

        p.put("birthdate", birthdateJohn);

        p.put("uniqDate", birthdateJohn);
        p.put("gender", new Integer(1));
        p.put("uniqChar", uniqChar);

        p.put("weight", new Double(85.7d));

        p.put("comment", new Text("This is a text field. It's a comment about this person."));

        p.put("uniqInt", uniqInt);

        Vector<Integer> intSet = new Vector<Integer>();
        intSet.addElement(new Integer(1));
        intSet.addElement(new Integer(0));
        p.put("intSet", intSet);

        p.put("brother", brother);
        p.put("uniqPtr", languages.get(0));
        Pointer person = t.insert("test.Person", p);

        p.clear();
        p.put("description", "");
        p.put("usagestart", birthdateJohn);
        p.put("email", "email1");
        t.insert(person, "address", p);

        // let's fill in the languages - we add them twice to have a meaningful test for distinct
        p.clear();
        Vector<Pointer> v = new Vector<Pointer>();
        for (Pointer l : languages) {
            v.add(l);
        }
        for (Pointer l : languages) {
            v.add(l);
        }
        p.put("speaks", v);
        t.update(brother, p);

        // let's add some toys
        p.clear();
        p.put("name", "car");
        t.insert(brother, "toys", p);
        p.clear();
        p.put("name", "doll");
        t.insert(brother, "toys", p);

    }

    public void deletePersonsAndIndividuals(Transaction t) {
        for (int i = 0; i < namesPersonIndivName.length; i++) {
            String query = "SELECT " + (t.getTransactionProvider().getQueryLanguage().equals("oql") ? "p" : "p.id")
                    + " AS p, p.indiv" + (t.getTransactionProvider().getQueryLanguage().equals("oql") ? "" : ".id")
                    + " as i FROM test.Person p WHERE p.indiv.name="
                    + (t.getTransactionProvider().getQueryLanguage().equals("oql") ? "$1" : "?");
            Vector<Dictionary<String, Object>> v = t.executeQuery(query, namesPersonIndivName[i]);
            if (v.size() > 0) {

                Vector<Pointer> emptyPointerVector = new Vector<Pointer>();
                
                // delete the languages
                Dictionary<String, Object> speaksDic = new Hashtable<String, Object>();
                speaksDic.put("speaks", emptyPointerVector);
                t.update((Pointer) v.firstElement().get("p"), speaksDic);

                // delete the address
                Dictionary<String, Object> addressDic = new Hashtable<String, Object>();
                speaksDic.put("address", emptyPointerVector);
                t.update((Pointer) v.firstElement().get("p"), addressDic);
                
                // delete the toys
                Dictionary<String, Object> toysDic = new Hashtable<String, Object>();
                speaksDic.put("toys", emptyPointerVector);
                t.update((Pointer) v.firstElement().get("p"), toysDic);
                

                t.delete((Pointer) v.firstElement().get("p"));
                t.delete((Pointer) v.firstElement().get("i"));
            }
        }
    }

    protected void insertLanguages(Transaction t) {
        languages.clear();
        Dictionary<String, Object> p = new Hashtable<String, Object>();
        for (int i = 0; i < languageData.length; i++) {
            p.put("name", languageData[i][0]);
            p.put("isoCode", languageData[i][1]);
            languages.add(t.insert("test.Language", p));
        }
    }

    protected void deleteLanguages(Transaction t) {
        String query = "SELECT " + (t.getTransactionProvider().getQueryLanguage().equals("oql") ? "l" : "l.id")
        + " AS l FROM test.Language l";
        Vector<Dictionary<String, Object>> v = t.executeQuery(query, new Object[] {});
        if (v.size() > 0) {
            for (Iterator<Dictionary<String, Object>> languages = v.iterator(); languages.hasNext();) {
                Dictionary<String, Object> dictionary = (Dictionary<String, Object>) languages.next();
                t.delete((Pointer) dictionary.get("l"));
            }
        }
    }

    public static void main(String[] args) {
        MakumbaTestData testData = new MakumbaTestData();
        Transaction t = TransactionProvider.getInstance().getConnectionTo(
            TransactionProvider.getInstance().getDefaultDataSourceName());
        if (args == null || args.length == 0 ||  args[0].equals("create")) {
            testData.insertLanguages(t);
            testData.insertPerson(t);
        } else if (args[0].equals("delete")) {
            testData.deletePersonsAndIndividuals(t);
            testData.deleteLanguages(t);
        }
        t.close();

        // also close data source completely
        t.getTransactionProvider().closeDataSource(t.getDataSource());
    }

}
