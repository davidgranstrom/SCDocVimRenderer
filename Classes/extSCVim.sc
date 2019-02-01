+ SCNvim {
    *prepareHelpFor {|text|
        var urlString, url, brokenAction;

        if (\SCDocVimRenderer.asClass.notNil
            and:{SCDoc.renderer != \SCDocVimRenderer.asClass}) {
            SCDoc.renderer = SCDocVimRenderer;
        };

        urlString = SCDoc.findHelpFile(text);
		url = URI(urlString);
        brokenAction = {
            "Sorry no help for %".format(text).postln;
            ^nil;
        };

        ^SCDoc.prepareHelpForURL(url) ?? brokenAction;
    }

    *openHelpFor {|text, vimPort|
        var msg, uri, path;
        uri = SCNvim.prepareHelpFor(text);
        path = uri.asLocalPath;
        if (uri.notNil and:{path.notNil}) {
            // help file
            msg = '{ "action": { "help": { "open": "%" } } }'.asString.format(path);
        } {
            // search for method
            msg = '{ "action": { "help": { "method": "%", "helpTargetDir": "%" } } }'.asString.format(uri.asString, SCDoc.helpTargetDir);
        };
        SCNvim.sendJSON(msg, vimPort);
    }
}
