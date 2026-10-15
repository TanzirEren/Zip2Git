package com.tenix.buildora.build;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Short, plain-language explanations for the most common compiler / aapt2 / D8 messages. */
public final class ErrorHints {
    private ErrorHints() {}

    private static final Pattern TYPE_UNRESOLVED = Pattern.compile("^(?:The type )?([A-Za-z_$][\\w$.<>?,\\[\\] ]*?) cannot be resolved to a type");
    private static final Pattern VAR_UNRESOLVED = Pattern.compile("^([A-Za-z_$][\\w$]*) cannot be resolved to a variable");
    private static final Pattern NOT_FIELD = Pattern.compile("^([A-Za-z_$][\\w$]*) cannot be resolved or is not a field");
    private static final Pattern SYMBOL = Pattern.compile("^([A-Za-z_$][\\w$]*) cannot be resolved$");

    /** Simple class name when the message says a type is missing (e.g. "TextView cannot be resolved to a type"). */
    public static String missingType(String m) {
        if (m == null) return null;
        Matcher t = TYPE_UNRESOLVED.matcher(m);
        if (t.find()) {
            String n = t.group(1).trim();
            if (n.indexOf('.') < 0 && n.indexOf('<') < 0 && !n.isEmpty()) return n;
        }
        return null;
    }

    /** A one or two sentence explanation, or null when there is nothing useful to add. */
    public static String hint(String m, String file) {
        if (m == null) return null;
        String t = missingType(m);
        if (m.startsWith("R cannot be resolved")) {
            return "'R' is generated from your res/ folder. Either an XML/resource file has an error (build again and read the FIRST error in the list), "
                    + "or the import for your app's R class is missing (tap Fix).";
        }
        if (t != null) {
            return "Java doesn't know the class '" + t + "'. Either the import is missing (tap Fix), the name has a typo, "
                    + "or the library that contains it is not added in Libraries.";
        }
        Matcher mm = NOT_FIELD.matcher(m);
        if (mm.find()) {
            String n = mm.group(1);
            return "'" + n + "' doesn't exist. If it's a resource (R.id." + n + ", R.string." + n + "…), make sure it is defined in a "
                    + "layout/values file with exactly this name, then build again. Otherwise check the spelling.";
        }
        mm = VAR_UNRESOLVED.matcher(m);
        if (mm.find()) return "The variable '" + mm.group(1) + "' was never declared here. Declare it first (for example 'int " + mm.group(1) + " = 0;') or fix the spelling.";
        mm = SYMBOL.matcher(m);
        if (mm.find()) return "'" + mm.group(1) + "' is not known at this point: a typo, a missing import, or a variable declared in another scope.";
        if (m.startsWith("The import ") && m.contains("cannot be resolved"))
            return "This import points to a package that doesn't exist. The library may not be added (open Libraries) or the class was renamed.";
        if (m.contains("Syntax error, insert \";\""))
            return "A semicolon is missing at the end of the statement - usually on the line above or on this one.";
        if (m.contains("Syntax error, insert \"}\"") || m.contains("Syntax error, insert \"}\" to complete"))
            return "A closing brace } is missing. Count your { and } - every opening brace needs a closing one.";
        if (m.contains("Syntax error, insert \")\""))
            return "A closing parenthesis ) is missing.";
        if (m.startsWith("Syntax error on token"))
            return "The compiler didn't expect this word or symbol here. Look at the line before it too - a missing ; or ) often shows up on the next line.";
        if (m.startsWith("Syntax error"))
            return "The code is not valid Java here. Check brackets, semicolons and quotes around this line.";
        if (m.startsWith("The method ") && m.contains("is undefined for the type"))
            return "That method doesn't exist on this class. Check the spelling and the arguments, or whether you meant a different object.";
        if (m.startsWith("The constructor ") && m.contains("is undefined"))
            return "No constructor accepts these arguments. Check how many arguments there are and their types.";
        if (m.startsWith("Type mismatch"))
            return "The value has a different type than the variable. Convert it (for example Integer.parseInt(text)) or change the variable type.";
        if (m.startsWith("Unhandled exception type"))
            return "This call can throw an exception. Wrap it in try { … } catch (Exception e) { … } or add 'throws' to the method.";
        if (m.contains("must implement the inherited abstract method"))
            return "Your class has to implement every method of the interface / abstract class it extends. Add the missing method(s).";
        if (m.contains("must override or implement a supertype method"))
            return "@Override is used but nothing to override: the method name or parameters don't match the parent class.";
        if (m.startsWith("This method must return a result of type") || m.contains("Missing return statement"))
            return "Every path of this method must end with a 'return' of the right type.";
        if (m.startsWith("Duplicate local variable"))
            return "A variable with this name already exists in this method. Use another name.";
        if (m.contains("may not have been initialized"))
            return "The variable is read before it always gets a value. Give it an initial value when you declare it.";
        if (m.startsWith("Unreachable code"))
            return "Code after return / break / throw can never run. Remove it or move it.";
        if (m.contains("Cannot make a static reference"))
            return "You're using something that belongs to an object from static code. Create an object first or make the member static.";
        if (m.startsWith("Void methods cannot return a value"))
            return "This method is declared 'void'. Remove the value after 'return', or change the return type.";
        if (m.contains("is not visible"))
            return "That member is private/protected. Make it public or add a getter.";
        if (m.contains("The public type") && m.contains("must be defined in its own file"))
            return "A public class must be in a file with exactly the same name (ClassName.java).";
        if (m.contains("is already defined") || m.contains("Duplicate method") || m.contains("Duplicate field"))
            return "The same name is defined twice in this class. Rename or remove one.";
        if (m.startsWith("The type ") && m.contains("is already defined"))
            return "Two classes have the same full name. Delete or rename one of them.";
        // ---- aapt2
        if (m.contains("resource") && m.contains("not found")) {
            Matcher r = Pattern.compile("resource ([\\w.\\-]+/[\\w.\\-]+)").matcher(m);
            String res = r.find() ? r.group(1) : "a resource";
            return "The resource '" + res + "' is used but doesn't exist. Add it under res/ (right folder, same name) or fix the reference.";
        }
        if (m.contains("attribute") && m.contains("not found"))
            return "Unknown XML attribute. Check the spelling - or it belongs to a library that is not added yet (for example Material or ConstraintLayout).";
        if (m.contains("style attribute") && m.contains("not found"))
            return "This theme/style needs a library (like AppCompat or Material) that is not added. Add it in Libraries or use a framework theme.";
        if (m.contains("duplicate value for resource"))
            return "The same resource name is defined twice in the same folder. Remove or rename one of them.";
        if (m.contains("not well-formed") || m.contains("XML") && m.contains("invalid"))
            return "The XML is broken: look for a missing > or \", an unclosed tag, or a stray character.";
        if (m.contains("unexpected element") && m.contains("found in"))
            return "This XML tag is not allowed here. Check the nesting and the tag name.";
        if (m.contains("failed linking references"))
            return "Some resources are referenced but missing (see the lines above), or an XML attribute is unknown.";
        if (m.contains("is defined multiple times") || m.contains("Type ") && m.contains("defined multiple times"))
            return "Two libraries contain the same class. Remove one of the libraries (often an old and a new version of the same one).";
        if (m.contains("Missing class") || m.contains("can't find referenced class"))
            return "A library needs a class that isn't available. Add the missing library, or add a '-dontwarn' rule in ProGuard rules.";
        if (m.contains("Dependency resolution failed") || m.contains("POM not found"))
            return "Buildora couldn't download a library. Check your internet connection and the library coordinate.";
        return null;
    }
}
