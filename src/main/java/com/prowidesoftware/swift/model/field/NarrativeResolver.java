package com.prowidesoftware.swift.model.field;

import com.prowidesoftware.swift.utils.IsoUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;

import java.math.BigDecimal;
import java.util.List;

/**
 * Implementation for {@link StructuredNarrativeField}
 *
 * @since 9.0.1
 */
public class NarrativeResolver {
    private static final transient java.util.logging.Logger log = java.util.logging.Logger.getLogger(NarrativeResolver.class.getName());

    private static final int CODEWORDTYPE_UCASE = 1;
    private static final int CODEWORDTYPE_UCASE_NUMBER = 2;
    private static final int CODEWORDTYPE_NUMBER = 3;

    /**
     * Parses the narrative text with a specific format depending on the field
     */
    public static Narrative parse(Field f) {
// enabled parser for any field until SRU2020 when NarrativeContainer is added for the generated fields model
//        if (f instanceof NarrativeContainer) {
            // each field support one or two line formats
            if (f.getName().equals(Field77A.NAME) || f.getName().equals(Field74.NAME) || f.getName().equals(Field86.NAME)) {
                return parseFormat1(f);
            } else if (f.getName().equals(Field72Z.NAME) || f.getName().equals(Field72.NAME) || f.getName().equals(Field77.NAME) || f.getName().equals(Field77J.NAME)) {
                return parseFormat2(f);
            } else if (f.getName().equals(Field73A.NAME) || f.getName().equals(Field71D.NAME) || f.getName().equals(Field71B.NAME) || f.getName().equals(Field73.NAME)) {
                return parseFormat3(f);
            } else if (f.getName().equals(Field77B.NAME)) {
                return parseFormat4(f);
            } else if (f.getName().equals(Field75.NAME) || f.getName().equals(Field76.NAME)) {
                return parseFormat5(f);
            } else if (f.getName().equals(Field49N.NAME) || f.getName().equals(Field45B.NAME) || f.getName().equals(Field46B.NAME) || f.getName().equals(Field49M.NAME)) {
                return parseFormat6(f);
            } else if (f.getName().equals(Field70.NAME) || f.getName().equals(Field77D.NAME) || f.getName().equals(Field37N.NAME)) {
                return parseFormat7(f);
            } else if (f.getName().equals(Field29A.NAME) || f.getName().equals(Field79.NAME)) {
                return parseFormat8(f.getValue());
            } else if (f.getName().equals(Field61.NAME)) {
                Field61 field61 = (Field61) f;
                return parseFormat8(field61.getSupplementaryDetails());
            }
            log.warning("Don't know how to parse structured narrative line formats for "+ f.getName());
//        } else {
//            log.warning("Field "+ f.getName() + " is not a " + NarrativeContainer.class.getSimpleName());
//        }
        return new Narrative();
    }

    private static Narrative parseFormat(Field f, int codewordMaxSize, int codewordType, boolean supportsCountry, boolean supportsCurrency, boolean supportsSupplement, boolean additionalNarrativesStartWithDoubleSlash) {

        Narrative narrative = new Narrative();

        String value = f.getValue();
        boolean unstructuredSection = !value.startsWith("/") || value.startsWith("//");

        StructuredNarrative structured = null;
        boolean firstSupplementAdded = false;
        for (String line : notEmptyLines(value)) {
            if (unstructuredSection) {
                narrative.addUnstructuredFragment(line);
                continue;
            }

            unstructuredSection = true;
            if (line.charAt(0) == '/') {
                String text;
                if (line.length() > 1 && line.charAt(1) == '/') {
                    unstructuredSection = false;
                    if (additionalNarrativesStartWithDoubleSlash) {
                        // continuation of current narrative
                        line = line.substring(2);

                        if (supportsSupplement) {
                            firstSupplementAdded = addNarrativeSupplement(firstSupplementAdded, line, structured);
                        } else if (StringUtils.isNotEmpty(line)) {
                            structured.addNarrativeFragment(line);
                        }
                    } else
                        structured.addNarrativeFragment(line);
                } else {
                    // new codeword
                    String codeword = StringUtils.substringBetween(line, "/", "/");
                    if (isCodewordValid(codeword, codewordType, codewordMaxSize)) {
                        firstSupplementAdded = false;
                        unstructuredSection = false;
                        structured = new StructuredNarrative().setCodeword(codeword);

                        text = StringUtils.substringAfter(line, codeword + "/");

                        if (supportsCountry) {
                            String country = getCountry(StringUtils.substringBefore(text, "//"));
                            if (country != null) {
                                structured.setCountry(country);
                                text = StringUtils.substringAfter(text, "//");
                            }
                        }

                        if (supportsCurrency) {
                            Triple<String, BigDecimal, String> tripleValue = getCurrencyAmountAndNarrative(text);
                            String currency = tripleValue.getLeft();
                            BigDecimal amount = tripleValue.getMiddle();
                            String narrativeFragment = tripleValue.getRight();

                            if (currency != null) {
                                structured.setCurrency(currency);
                                if (amount != null) {
                                    structured.setAmount(amount);
                                }
                            }
                            text = narrativeFragment;
                        }

                        if (supportsSupplement) {
                            firstSupplementAdded = addNarrativeSupplement(firstSupplementAdded, text, structured);
                        } else if (StringUtils.isNotEmpty(text)) {
                            structured.addNarrativeFragment(text);
                        }

                        narrative.add(structured);
                    } else if (!additionalNarrativesStartWithDoubleSlash && structured != null) {
                        structured.addNarrativeFragment(line);
                        unstructuredSection = false;
                    }
                }
            } else if (!additionalNarrativesStartWithDoubleSlash && structured != null) {
                structured.addNarrativeFragment(line);
                unstructuredSection = false;
            }
            if (unstructuredSection)
                narrative.addUnstructuredFragment(line);
        }

        return narrative;
    }

    private static List<String> notEmptyLines(String value) {
        List<String> lines = SwiftParseUtils.getLines(value);
        lines.removeIf(item -> item == null || item.isEmpty());
        return lines;
    }

    //returns true if it's the first supplement added
    private static boolean addNarrativeSupplement(boolean firstSupplementAdded, String line, StructuredNarrative structured) {
        if (!firstSupplementAdded) {
            //narrative
            String text = StringUtils.substringBefore(line, "/");
            if (StringUtils.isNotEmpty(text)) {
                structured.addNarrativeFragment(text);
            }

            //first supplement
            text = StringUtils.substringAfter(line, "/");
            if (StringUtils.isNotEmpty(text)) {
                structured.addNarrativeSupplementFragment(text);
                return true;
            }
        } else {
            // additional supplement
            if (StringUtils.isNotEmpty(line)) {
                structured.addNarrativeSupplementFragment(line);
            }
        }
        return firstSupplementAdded;
    }

    /**
     * Free format codes in slashes, not necessary on new lines
     */
    public static Narrative parseFreeFormat(String value) {
        Narrative narrative = new Narrative();

        if (value == null) {
            return narrative;
        }

        boolean structured = value.startsWith("/") && isCodewordValid(StringUtils.substringBefore(value.substring(1), "/"), CODEWORDTYPE_UCASE, -1);

        List<String> lines = notEmptyLines(value);
        if (structured) {
            // parse structured items detecting codewords
            String[] tokens = String.join("", lines).split("/");
            String currentCodeword = null;
            StringBuilder currentText = new StringBuilder();
            for (String token : tokens) {
                if (isCodewordValid(token, CODEWORDTYPE_UCASE, -1)) {
                    if (currentCodeword != null) {
                        // store current structured item
                        add(narrative, currentCodeword, currentText.toString());
                    }
                    currentCodeword = token;
                    currentText = new StringBuilder();

                } else {
                    if (currentText.length() > 0) {
                        // add the separator back if it was present between the narrative text
                        currentText.append("/");
                    }
                    currentText.append(token);
                }
            }
            if (currentCodeword != null) {
                // add the last created item if necessary
                add(narrative, currentCodeword, currentText.toString());
            }

        } else {
            // set lines as unstructured content
            for (String line : lines) {
                narrative.addUnstructuredFragment(line);
            }
        }

        return narrative;
    }

    /**
     * Adds a structured narrative item to the narrative
     * @param narrative the narrative where item is added
     * @param codeword a codeword
     * @param text the narrative text or blank to skip
     */
    private static void add(Narrative narrative, String codeword, String text) {
        StructuredNarrative item = new StructuredNarrative().setCodeword(codeword);
        if (StringUtils.isNoneBlank(text)) {
            item.addNarrativeFragment(text);
        }
        narrative.add(item);
    }

    private static boolean isCodewordValid(String codeword, int codewordType, int codewordMaxSize) {
        if (StringUtils.isEmpty(codeword))
            return false;
        codeword = StringUtils.trimToEmpty(codeword);

        //Maxlength
        if (codewordMaxSize > 0 && codeword.length() > codewordMaxSize)
            return false;

        //Type
        for(int i=0; i<codeword.length(); i++){
            char c = codeword.charAt(i);
            if (!Character.isLetterOrDigit(c))
                return false;
            else if (Character.isLowerCase(c) && (codewordType == CODEWORDTYPE_UCASE || codewordType == CODEWORDTYPE_UCASE_NUMBER))
                return false;
            else if (Character.isLetter(c) && (codewordType == CODEWORDTYPE_NUMBER))
                return false;
            else if (Character.isDigit(c) && (codewordType == CODEWORDTYPE_UCASE))
                return false;
        }

        return true;
    }

    private static String getCountry(String text) {
        text = StringUtils.trimToEmpty(text);
        if (!IsoUtils.getInstance().isValidISOCountry(text))
            return null;
        return text;
    }

    private static Triple<String, BigDecimal, String> getCurrencyAmountAndNarrative(String text) {
        StringBuilder currency = new StringBuilder();
        StringBuilder amount = new StringBuilder();
        StringBuilder narrative = new StringBuilder();

        int section = 1;
        text = StringUtils.trimToEmpty(text);
        for(int i=0; i<text.length(); i++){
            char c = text.charAt(i);
            switch (section) {
                case 1: //currency section
                    if (Character.isDigit(c)){
                        section = 2;
                        amount.append(c);
                    } else
                        currency.append(c);
                    break;
                case 2: //amount section
                    if (Character.isDigit(c) || c == '.' || c == ',')
                        amount.append((c == ',') ? '.' : c);
                    else {
                        section = 3;
                        narrative.append(c);
                    }
                    break;
                case 3: //narrative section
                    narrative.append(c);
                    break;
            }
        }

        return new ImmutableTriple<String, BigDecimal, String>
                ((currency.length() == 0) ? null : currency.toString(),
                        (amount.length() == 0) ? null : new BigDecimal(amount.toString()),
                        (narrative.length() == 0) ? null : narrative.toString());
    }

    /**
     * Line 1: 	    /8a/[additional information] 			    (Code)(Narrative)
     * Lines 2-n:   /8a/[additional information] 			    (Code)(Narrative)
     *              [//continuation of additional information] 	(Narrative)
     */
    public static Narrative parseFormat1(Field f) {
        return parseFormat(f, 8, CODEWORDTYPE_UCASE, false, false, false, true);
    }

    /**
     * Line 1: 	    /8c/[additional information] 			    (Code)(Narrative)
     * Lines 2-n:   /8c/[additional information] 			    (Code)(Narrative)
     *              [//continuation of additional information] 	(Narrative)
     */
    public static Narrative parseFormat2(Field f) {
        return parseFormat(f, 8, CODEWORDTYPE_UCASE_NUMBER, false, false, false, true);
    }

    /**
     * Line 1: 	    /8c/[3!a13d][additional information] 		(Code)(Currency)(Amount)(Narrative)
     * Lines 2-6: 	/8c/[3!a13d][additional information] 		(Code)(Currency)(Amount)(Narrative)
     *              [//continuation of additional information] 	(Narrative)
     */
    public static Narrative parseFormat3(Field f) {
        return parseFormat(f, 8, CODEWORDTYPE_UCASE_NUMBER, false, true, false, true);
    }

    /**
     * Line 1: 	    /8c/[additional information] 			    (Code)(Narrative)
     * Lines 2-3: 	[//continuation of additional information] 	(Narrative)
     * Variant for cat 1 with country
     * Line 1: 	    /8c/2!a[//additional information] 		    (Code)(Country)(Narrative)
     * Lines 2-3: 	[//continuation of additional information] 	(Narrative)
     */
    public static Narrative parseFormat4(Field f) {
        return parseFormat(f, 8, CODEWORDTYPE_UCASE_NUMBER, true, false, false, true);
    }

    /**
     * Line 1:		/2n/[supplement 1][/supplement2]		        (Query Number)(Narrative 1)(Narrative 2)
     * Lines 2-6	/2n/[supplement 1][/supplement2]
     *              [//continuation of supplementary information]
     */
    public static Narrative parseFormat5(Field f) {
        return parseFormat(f, 2, CODEWORDTYPE_NUMBER, false, false, true, true);
    }

    /**
     * Line 1: 	/6c/[additional information] 		(Code)(Narrative)
     * Lines 2-100:	/6c/[additional information] 	(Code)(Narrative)
     * [continuation of additional information] 	(Narrative) (cannot start with slash)
     */
    public static Narrative parseFormat6(Field f) {
        return parseFormat(f, 6, CODEWORDTYPE_UCASE_NUMBER, false, false, false, false);
    }

    /**
     * Code between slashes at the beginning of a line
     */
    public static Narrative parseFormat7(Field f) {
        return parseFormat(f, -1, CODEWORDTYPE_UCASE, false, false, false, true);
    }

    /**
     * @see #parseFreeFormat(String)
     */
    public static Narrative parseFormat8(String value) {
        return parseFreeFormat(value);

    }

}
