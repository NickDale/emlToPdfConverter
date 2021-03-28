package parser.interfaces;

import java.util.regex.Matcher;

@FunctionalInterface
public interface Replacer {

    String replace(Matcher match) throws Exception;

}
