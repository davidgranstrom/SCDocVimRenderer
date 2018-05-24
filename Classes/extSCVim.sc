+ SCVim {
    *prepareHelpFor {|text|
        var urlString = SCDoc.findHelpFile(text);
		var url = URI(urlString);
        var brokenAction = {
            "Sorry no help for %".format(text).postln;
        };

        SCDoc.prepareHelpForURL(url) ?? brokenAction;
    }
}
