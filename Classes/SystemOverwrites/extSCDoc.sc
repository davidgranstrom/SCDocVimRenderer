+ SCDoc {
	*exportDocMapJS {|path|
        var f, numItems;
        if (\SCDocVimRenderer.asClass.notNil
            and:{SCDoc.renderer == \SCDocVimRenderer.asClass})
        {
            path = SCDoc.helpTargetDir +/+ "docmap.json";
            f = File.open(path,"w");
            numItems = this.documents.size - 1;
            f << "{\n";
            this.documents.do {|doc, i|
                doc.toJSON(f, i >= numItems);
            };
            f << "}\n";
            f.close;
        } {
            // original method
            f = File.open(path,"w");
            f << "docmap = {\n";
            this.documents.do {|doc|
                doc.toJSON(f);
            };
            f << "}\n";
            f.close;
        };
	}
}

+ SCDocEntry {
    *useVimRenderer {
        ^(\SCDocVimRenderer.asClass.notNil
		and:{SCDoc.renderer == \SCDocVimRenderer.asClass
		and:{\SCNvim.asClass.notNil
		and:{SCNvim.vimHelp}}})
    }

	destPath {
		if (SCDocEntry.useVimRenderer) {
			^SCDoc.helpTargetDir +/+ path ++ ".txt";
		} {
			^SCDoc.helpTargetDir +/+ path ++ ".html";
		}
	}

	makeMethodList {
		var list;
		docimethods.do {|name|
			list = list.add("-"++name.asString);
		};
		doccmethods.do {|name|
			list = list.add("*"++name.asString);
		};
		undocimethods.do {|name|
			list = list.add("?-"++name.asString);
		};
		undoccmethods.do {|name|
			list = list.add("?*"++name.asString);
		};
		docmethods.do {|name|
			list = list.add("."++name.asString);
		};
		^list;
	}

	// overriden to output valid json
	prJSONString {|stream, key, x|
		if (x.isNil) { x = "" };
		stream << "\"" << key << "\": \"" << x.escapeChar(34.asAscii) << "\",\n";
	}

	// overriden to output valid json
	prJSONList {|stream, key, v, lastItem|
		var delimiter = if(lastItem.notNil and:{lastItem}, "", ",");
		if (v.isNil) { v = "" };
		stream << "\"" << key << "\": [ " << v.collect{|x|"\""++x.escapeChar(34.asAscii)++"\""}.join(",") << " ]%\n".format(delimiter);
	}

	toJSON {|stream, lastItem|
		var delimiter = if(lastItem.notNil and:{lastItem}, "", ",");
		var inheritance = [];
		var numItems;

		stream << "\"" << path.escapeChar(34.asAscii) << "\": {\n";

		this.prJSONString(stream, "title", title);
		this.prJSONString(stream, "path", path);
		this.prJSONString(stream, "summary", summary);
		this.prJSONString(stream, "installed", if(isExtension,"extension","standard")); //FIXME: also 'missing'.. better to have separate extension and missing booleans..
		this.prJSONString(stream, "categories", if(categories.notNil) {categories.join(", ")} {""}); // FIXME: export list instead
		this.prJSONList(stream, "keywords", keywords);
		this.prJSONList(stream, "related", related);

		this.prJSONList(stream, "methods", this.makeMethodList, klass.isNil);

		if (oldHelp.notNil) {
			this.prJSONString(stream, "oldhelp", oldHelp);
		};

		if (klass.notNil) {
			var keys = #[ "superclasses", "subclasses", "implementor" ];
			klass.superclasses !? {
				inheritance = inheritance.add(klass.superclasses.collect {|c|
					c.name.asString
				});
			};
			klass.subclasses !? {
				inheritance = inheritance.add(klass.subclasses.collect {|c|
					c.name.asString
				});
			};
			implKlass !? {
				inheritance = inheritance.add(implKlass.name.asString);
			};

			numItems = inheritance.size - 1;
			inheritance.do {|item, i|
				this.prJSONList(stream, keys[i], item, i >= numItems);
			};
		};

		stream << "}%\n".format(delimiter);
	}
}
