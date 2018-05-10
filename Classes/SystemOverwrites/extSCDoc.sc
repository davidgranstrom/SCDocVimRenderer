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
}

+ SCDocEntry {
	destPath {
		^SCDoc.helpTargetDir +/+ path ++ ".txt";
	}
}
