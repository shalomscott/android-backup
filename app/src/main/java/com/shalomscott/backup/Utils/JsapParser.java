package com.shalomscott.backup.Utils;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.ParseException;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.StringParser;
import com.martiansoftware.jsap.Switch;
import com.martiansoftware.jsap.UnflaggedOption;
import com.martiansoftware.jsap.stringparsers.StringStringParser;


public class JsapParser {
    private static JSAP jsapParser;

    public static JSAP getInstance() throws JSAPException {
        if (jsapParser != null) {
            return jsapParser;
        }

        UnflaggedOption filepath = new UnflaggedOption("filepath", JSAP.STRING_PARSER, true,
                "The relative path to the file/directory");
//        FlaggedOption hash = new FlaggedOption("hash", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, false, 'h',
//                "hash", "Hash of the file, used to detect file change");

        jsapParser = new JSAP();
        jsapParser.registerParameter(filepath);
//        jsapParser.registerParameter(hash);

        return jsapParser;
    }
}
