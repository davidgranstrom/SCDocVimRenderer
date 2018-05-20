+ SCDoc {
    *findHelpFile {|str|
        var old, sym, pfx = SCDoc.helpTargetUrl;

        if(str.isNil or: {str.isEmpty}) { ^pfx ++ "/Help.txt" };
        if(this.documents[str].notNil) { ^pfx ++ "/" ++ str ++ ".txt" };

        sym = str.asSymbol;
        if(sym.asClass.notNil) {
            ^pfx ++ (if(this.documents["Classes/"++str].isUndocumentedClass) {
                (old = if(Help.respondsTo('findHelpFile'),{Help.findHelpFile(str)})) !? {
                    "/OldHelpWrapper.txt#"++old++"?"++SCDoc.helpTargetUrl ++ "/Classes/" ++ str ++ ".txt"
                }
            } ?? { "/Classes/" ++ str ++ ".txt" });
        };

        if(str.last == $_) { str = str.drop(-1) };
        ^pfx ++ if("^[a-z][a-zA-Z0-9_]*$|^[-<>@|&%*+/!?=]+$".matchRegexp(str))
        { "/Overviews/Methods.txt#" } { "/Search.txt#" } ++ str;
    }

    *prepareHelpForURL {|url|
        var path, targetBasePath, pathIsCaseInsensitive;
        var subtarget, src, c, cmd, doc, destExist, destMtime;
        var verpath = this.helpTargetDir +/+ "version";

        path = url.asLocalPath;

        // detect old helpfiles and wrap them in OldHelpWrapper
        if(url.scheme == "sc") { ^URI(SCDoc.findHelpFile(path)); };

        // just pass through remote url's
        if(url.scheme != "file") {^url};

        targetBasePath = SCDoc.helpTargetDir;
        if (thisProcess.platform.name === \windows)
        { targetBasePath = targetBasePath.replace("/","\\") };
        pathIsCaseInsensitive = thisProcess.platform.name === \windows;

        // detect old helpfiles and wrap them in OldHelpWrapper
        if(
            /*
            // this didn't work for quarks due to difference between registered old help path and the quarks symlink in Extensions.
            // we could use File.realpath(path) below but that would double the execution time,
            // so let's just assume any local file outside helpTargetDir is an old helpfile.
            block{|break|
                Help.do {|key, path|
                    if(url.endsWith(path)) {
                        break.value(true)
                    }
                }; false
            }*/
            compare(
                path [..(targetBasePath.size-1)],
                targetBasePath,
                pathIsCaseInsensitive
            ) != 0
        ) {
            ^SCDoc.getOldWrapUrl(url)
        };

        if(destExist = File.exists(path))
        {
            destMtime = File.mtime(path);
        };

        if(path.endsWith(".txt")) {
            subtarget = path.drop(this.helpTargetDir.size+1).drop(-4).replace("\\","/");
            doc = this.documents[subtarget];
            doc !? {
                if(doc.isUndocumentedClass) {
                    if(doc.mtime == 0) {
                        this.renderUndocClass(doc);
                        doc.mtime = 1;
                    };
                    ^url;
                };
                if(File.mtime(doc.fullPath)>doc.mtime) { // src changed after indexing
                    this.postMsg("% changed, re-indexing documents".format(doc.path),2);
                    this.indexAllDocuments;
                    ^this.prepareHelpForURL(url);
                };
                if(destExist.not
                    or: {doc.mtime>destMtime}
                    or: {doc.additions.detect {|f| File.mtime(f)>destMtime}.notNil}
                    or: {File.mtime(this.helpTargetDir +/+ "scdoc_version")>destMtime}
                    or: {doc.klass.notNil and: {File.mtime(doc.klass.filenameSymbol.asString)>destMtime}}
                ) {
                    this.parseAndRender(doc);
                };
                ^url;
            };
        };

        if(destExist) {
            ^url;
        };

        warn("SCDoc: Broken link:" + url.asString);
        ^nil;
    }
}

+ SCDocEntry {
	destPath {
		^SCDoc.helpTargetDir +/+ path ++ ".txt";
	}
}
