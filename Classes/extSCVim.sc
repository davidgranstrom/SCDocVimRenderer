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
        var uri = SCNvim.prepareHelpFor(text);
        var msg = '{ "action": { "help": { "open": "%" } } }'.asString.format(uri.asLocalPath);
        SCNvim.sendJSON(msg, vimPort);
    }
}
