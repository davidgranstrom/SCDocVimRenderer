SCDocVimRenderer {
	classvar <binaryOperatorCharacters = "!@%&*-+=|<>?/";
	classvar currentClass, currentImplClass, currentMethod, currArg;
	classvar currentNArgs;
	classvar footNotes;
	classvar noParBreak;
	classvar currDoc;
	classvar minArgs;
	classvar baseDir;

    *wrapString {|str, maxWidth=78|
        var output = "";
        var wrap = false;

        str.size.do {|i|
            var char = str[i];
            if (((i+1) % 78) == 0) {
                wrap = true;
            };
            if (wrap and:{char == Char.space}) {
                output = output ++ "\n";
                wrap = false;
            } {
                output = output ++ str[i];
            }
        };

        ^output;
    }

	*escapeSpecialChars {|str|
        ^str;
	}

	*escapeSpacesInAnchor { |str|
        ^str;
	}

	// Find the target (what goes after href=) for a link that stays inside the hlp system
	*prLinkTargetForInternalLink { |linkBase, linkAnchor, originalLink|
		var doc, result;

		if(linkBase.isEmpty) {
			result = "";
		} {
			doc = SCDoc.documents[linkBase];
			result = baseDir +/+ linkBase;

			// If this is an existing document, just add .html to get the target
			if(doc.notNil) {
				result = result ++ ".txt"
			} {
				// If the document doesn't exist according to SCDoc, check the filesystem
				// to see if the link target is present
				if(File.exists(SCDoc.helpTargetDir +/+ linkBase ++ ".txt")) {
					result = result ++ ".txt"
				} {
					// If the link target doesn't exist as an HTML file, check to see if the
					// raw filepath exists. If it does, do nothing with it -- we're done. If
					// it doesn't, then consider this a broken link.
					if(File.exists(SCDoc.helpTargetDir +/+ linkBase).not) {
						"SCDoc: In %\n"
						"  Broken link: '%'"
						.format(currDoc.fullPath, originalLink).warn;
					};
				};
			};
		};

		if(linkAnchor.isEmpty) {
			^result
		} {
			^result ++ "#" ++ this.escapeSpacesInAnchor(linkAnchor);
		}
	}

	// Creates a link target for a link that points outside of the help system
	*prLinkTargetForExternalLink { |linkBase, linkAnchor|
		if(linkAnchor.isEmpty) {
			^linkBase
		} {
			^linkBase ++ "#" ++ this.escapeSpacesInAnchor(linkAnchor);
		}
	}

	// Find the text label for the given link, which points inside the help system.
	*prLinkTextForInternalLink { |linkBase, linkAnchor, linkText|
		var doc, result;
		// Immediately return link text if available
		if(linkText.isEmpty.not) {
			^linkText
		};

		// If the base was non-empty, generate it by combining the filename and the anchor.
		// Otherwise, if there was an anchor, use that. Otherwise, use "(empty link)"
		if(linkBase.isEmpty) {
			if(linkAnchor.isEmpty) {
				^"(empty link)"
			} {
				^linkAnchor
			}
		} {
			doc = SCDoc.documents[linkBase];
			result = doc !? _.title ? linkBase.basename;
			if(linkAnchor.isEmpty) {
				^result
			} {
				^result ++ ": " ++ linkAnchor
			}
		}
	}

	// argument link: the raw link text from the schelp document
	// argument escape: whether or not to escape special characters in the link text itself
	// returns: the <a> tag HTML representation of the original `link`
	// Possible, non-exhaustive input types for `link`:
	//   "#-decorator#decorator"
	//   "#-addAction"
	//   "Classes/View#-front#shown"
	//   "Guides/GUI-Introduction#view"
	//   "Classes/FlowLayout"
	//   "#*currentDrag#drag&drop data"
	//   "#Key actions"
	//   "http://qt-project.org/doc/qt-4.8/qt.html#Key-enum"
	*htmlForLink { |link, escape = true|
        ^"|%|".format(link.split($/)[1]);
	}

	*makeArgString {|m, par=true|
		var res = "";
		var value;
		var l = m.argNames;
		var last = l.size-1;
		l.do {|a,i|
			if (i>0) { //skip 'this' (first arg)
				if(i==last and: {m.varArgs}) {
					res = res ++ " " ++ "... " ++ a;
				} {
					if (i>1) { res = res ++ ", " };
					res = res ++ a;
					(value = m.prototypeFrame[i]) !? {
						value = if(value.class===Float) { value.asString } { value.cs };
						res = res ++ ": " ++ value;
					};
				};
				res = res ++ "";
			};
		};
		if (res.notEmpty and: par) {
			^("("++res++")");
		};
		^res;
	}

	*renderHeader {|stream, doc, body|
		var x, cats, m, z;
		var thisIsTheMainHelpFile;
		var folder = doc.path.dirname;
		var undocumented = false;
		var displayedTitle;
		if(folder==".",{folder=""});

		// FIXME: use SCDoc.helpTargetDir relative to baseDir
		baseDir = ".";
		doc.path.occurrencesOf($/).do {
			baseDir = baseDir ++ "/..";
		};

		thisIsTheMainHelpFile = (doc.title == "Help") and: {
			(folder == "") or:
			{ (thisProcess.platform.name === \windows) and: { folder == "Help" } }
		};

		stream << "*%* ".format(doc.title);

		if(thisIsTheMainHelpFile) {
			stream << "SuperCollider " << Main.version << " Help";
		} {
			stream << "| SuperCollider " << Main.version << " | Help";
		};

		stream
		// << doc.title.escapeChar($') << "\n"
		<< "Version: " << Main.version << "\n";

		doc.related !? {
			stream << "\nSee also: "
			<< (doc.related.collect {|r| this.htmlForLink(r)}.join(" "))
			<< "\n";
		};

        stream << "\n";
	}

	*renderChildren {|stream, node|
		node.children.do {|child| this.renderSubTree(stream, child) };
	}

	*renderMethod {|stream, node, methodType, cls, icls|
		var methodTypeIndicator;
		var methodCodePrefix;
		var args = node.text ?? ""; // only outside class/instance methods
		var names = node.children[0].children.collect(_.text);
		var mstat, sym, m, m2, mname2;
		var lastargs, args2;
		var x, maxargs = -1;
		var methArgsMismatch = false;

		methodTypeIndicator = switch(
			methodType,
			\classMethod, { "* " },
			\instanceMethod, { "- " },
			\genericMethod, { "" }
		);

		minArgs = inf;
		currentMethod = nil;
		names.do {|mname|
			methodCodePrefix = switch(
				methodType,
				\classMethod, { if(cls.notNil) { cls.name.asString[5..] } { "" } ++ "" },
				\instanceMethod, {
					// If the method name contains any valid binary operator character, remove the
					// "." to reduce confusion.
					if(mname.asString.any(this.binaryOperatorCharacters.contains(_)), { "" }, { "." })
				},
				\genericMethod, { "" }
			);

			mname2 = this.escapeSpecialChars(mname);
			if(cls.notNil) {
				mstat = 0;
				sym = mname.asSymbol;
				//check for normal method or getter
				m = icls !? {icls.findRespondingMethodFor(sym.asGetter)};
				m = m ?? {cls.findRespondingMethodFor(sym.asGetter)};
				m !? {
					mstat = mstat | 1;
					args = this.makeArgString(m);
					args2 = m.argNames !? {m.argNames[1..]};
				};
				//check for setter
				m2 = icls !? {icls.findRespondingMethodFor(sym.asSetter)};
				m2 = m2 ?? {cls.findRespondingMethodFor(sym.asSetter)};
				m2 !? {
					mstat = mstat | 2;
					args = m2.argNames !? {this.makeArgString(m2,false)} ?? {"value"};
					args2 = m2.argNames !? {m2.argNames[1..]};
				};
				maxargs.do {|i|
					var a = args2[i];
					var b = lastargs[i];
					if(a!=b and: {a!=nil} and: {b!=nil}) {
						methArgsMismatch = true;
					}
				};
				lastargs = args2;
				case
					{args2.size>maxargs} {
						maxargs = args2.size;
						currentMethod = m2 ?? m;
					}
					{args2.size<minArgs} {
						minArgs = args2.size;
					};
			} {
				m = nil;
				m2 = nil;
				mstat = 1;
			};

			x = {
				stream << "\n" << methodCodePrefix
				<< methodTypeIndicator << "`" << mname << "`"
				// << baseDir << "/Overviews/Methods.html#"
				// << mname2 << "'>" << mname2 << "</a>"
			};

			switch (mstat,
				// getter only
				1, { x.value; stream << args; },
				// getter and setter
				3, { x.value; },
				// method not found
				0, {
					"SCDoc: In %\n"
					"  Method %% not found.".format(currDoc.fullPath, methodTypeIndicator, mname2).warn;
					x.value;
					stream << ": METHOD NOT FOUND!";
				}
			);

			stream << "\n";

			// has setter
			if(mstat & 2 > 0) {
				x.value;
				if(args2.size<2) {
					stream << " = " << args << "\n";
				} {
					stream << "_(" << args << ")\n";
				}
			};

			m = m ?? m2;
			m !? {
				if(m.isExtensionOf(cls) and: {icls.isNil or: {m.isExtensionOf(icls)}}) {
					stream << "\nFrom extension in "
					<< URI.fromLocalPath(m.filenameSymbol.asString).asString
					<< m.filenameSymbol << "\n";
				} {
					if(m.ownerClass == icls) {
						stream << "\nFrom implementing class\n";
					} {
						if(m.ownerClass != cls) {
							m = m.ownerClass.name;
							m = if(m.isMetaClassName) {m.asString.drop(5)} {m};
							stream << "\nFrom superclass: "
							// << baseDir << "/Classes/" << m << ".txt'>" << m << "\n";
							<< "|" << m << "|" << "\n";
						}
					}
				};
			};
		};

		if(methArgsMismatch) {
			"SCDoc: In %\n"
			"  Grouped methods % do not have the same argument signature."
			.format(currDoc.fullPath, names).warn;
		};

		// ignore trailing mul add arguments
		if(currentMethod.notNil) {
			currentNArgs = currentMethod.argNames.size;
			if(currentNArgs > 2
			and: {currentMethod.argNames[currentNArgs-1] == \add}
			and: {currentMethod.argNames[currentNArgs-2] == \mul}) {
				currentNArgs = currentNArgs - 2;
			}
		} {
			currentNArgs = 0;
		};

		if(node.children.size > 1) {
			stream << "\n";
			this.renderChildren(stream, node.children[1]);
			stream << "\n";
		};
		currentMethod = nil;
	}

	*renderSubTree {|stream, node|
		var f, z, img;
		switch(node.id,
			\PROSE, {
				if(noParBreak) {
					noParBreak = false;
				} {
					stream << "\n";
				};
				this.renderChildren(stream, node);
			},
			\NL, { }, // these shouldn't be here..
// Plain text and modal tags
			\TEXT, {
                // noParBreak = true;
				// stream << this.escapeSpecialChars(node.text);
				stream << this.wrapString(node.text, 78);
			},
			\LINK, {
				stream << this.htmlForLink(node.text);
			},
			\CODEBLOCK, {
                var code = node.text;
                // indent code blocks so that vim hightlights them verbatim
                code = code.split($\n).collect {|fragment| "    " ++ fragment ++ "\n" }.join;
                stream << "\n>\n" << code << "\n<\n";
			},
			\CODE, {
				stream << "`" << this.escapeSpecialChars(node.text) << "`"
			},
			\EMPHASIS, {
				stream << "'" << this.escapeSpecialChars(node.text) << "'"
			},
			\TELETYPEBLOCK, {
				stream << "`" << this.escapeSpecialChars(node.text) << "`"
			},
			\TELETYPE, {
				stream << this.escapeSpecialChars(node.text);
			},
			\STRONG, {
				stream << this.escapeSpecialChars(node.text);
			},
			\SOFT, {
				stream << this.escapeSpecialChars(node.text);
			},
			\ANCHOR, {
				stream << node.text;
			},
			\KEYWORD, {
				node.children.do {|child|
					// stream << "<a class='anchor' name='kw_" << this.escapeSpacesInAnchor(child.text) << "'>&nbsp;</a>";
                    stream << child.text << " ";
				}
			},
			\IMAGE, {
				// f = node.text.split($#);
				// stream << "<div class='image'>";
				// img = "<img src='" ++ f[0] ++ "'/>";
				// if(f[2].isNil) {
				// 	stream << img;
				// } {
				// 	stream << this.htmlForLink(f[2]++"#"++(f[3]?"")++"#"++img,false);
				// };
				// f[1] !? { stream << "<br><b>" << f[1] << "</b>" }; // ugly..
				// stream << "</div>\n";
			},
// Other stuff
			\NOTE, {
				stream << "\nNOTE: ";
				noParBreak = true;
				this.renderChildren(stream, node);
				// stream << "\n";
			},
			\WARNING, {
				stream << "WARNING: ";
				noParBreak = true;
				this.renderChildren(stream, node);
				stream << "\n";
			},
			\FOOTNOTE, {
				footNotes = footNotes.add(node);
                stream << footNotes.size;
				// stream << "<a class='footnote anchor' name='footnote_org_"
				// << footNotes.size
				// << "' href='#footnote_"
				// << footNotes.size
				// << "'><sup>"
				// << footNotes.size
				// << "</sup></a> ";
			},
			\CLASSTREE, {
				// stream << "<ul class='tree'>";
				this.renderClassTree(stream, node.text.asSymbol.asClass);
				// stream << "</ul>";
			},
// Lists and tree
			\LIST, {
				// stream << "\n\n";
				this.renderChildren(stream, node);
				// stream << "\n\n";
			},
			\TREE, {
				// stream << "<ul class='tree'>\n";
				stream << "\n\n";
				this.renderChildren(stream, node);
				stream << "\n\n";
				// stream << "</ul>\n";
			},
			\NUMBEREDLIST, {
				// stream << "<ol>\n";
				stream << "\n\n";
				this.renderChildren(stream, node);
				stream << "\n\n";
				// stream << "</ol>\n";
			},
			\ITEM, { // for LIST, TREE and NUMBEREDLIST
				noParBreak = true;
				this.renderChildren(stream, node);
				stream << "\n";
			},
// Definitionlist
			\DEFINITIONLIST, {
				// stream << "<dl>\n";
				this.renderChildren(stream, node);
				// stream << "</dl>\n";
			},
			\DEFLISTITEM, {
				this.renderChildren(stream, node);
			},
			\TERM, {
				// stream << "<dt>";
				noParBreak = true;
				this.renderChildren(stream, node);
			},
			\DEFINITION, {
				// stream << "<dd>";
				noParBreak = true;
				this.renderChildren(stream, node);
			},
// Tables
			\TABLE, {
				// stream << "<table>\n";
				this.renderChildren(stream, node);
				// stream << "</table>\n";
			},
			\TABROW, {
				// stream << "<tr>";
				this.renderChildren(stream, node);
			},
			\TABCOL, {
				// stream << "<td>";
				noParBreak = true;
				this.renderChildren(stream, node);
			},
// Methods
			\CMETHOD, {
				this.renderMethod(
					stream, node,
					\classMethod,
					currentClass !? {currentClass.class},
					currentImplClass !? {currentImplClass.class}
				);
			},
			\IMETHOD, {
				this.renderMethod(
					stream, node,
					\instanceMethod,
					currentClass,
					currentImplClass
				);
			},
			\METHOD, {
				this.renderMethod(
					stream, node,
					\genericMethod,
					nil, nil
				);
			},
			\CPRIVATE, {},
			\IPRIVATE, {},
			\COPYMETHOD, {},
			\CCOPYMETHOD, {},
			\ICOPYMETHOD, {},
			\ARGUMENTS, {
				stream << "ARGUMENTS~";
				currArg = 0;
				if(currentMethod.notNil and: {node.children.size < (currentNArgs-1)}) {
					"SCDoc: In %\n"
					"  Method %% has % args, but doc has % argument:: tags.".format(
						currDoc.fullPath,
						if(currentMethod.ownerClass.isMetaClass) {"*"} {"-"},
						currentMethod.name,
						currentNArgs-1,
						node.children.size,
					).warn;
				};
                // stream << "\n>";
                stream << "\n";
				this.renderChildren(stream, node);
                // stream << "\n<";
                stream << "\n";
			},
			\ARGUMENT, {
				currArg = currArg + 1;
				noParBreak = true;
                stream << "\n'";
                // stream << "\t";
				if(node.text.isNil) {
					currentMethod !? {
						if(currentMethod.varArgs and: {currArg==(currentMethod.argNames.size-1)}) {
							stream << "... ";
						};
						stream << if(currArg < currentMethod.argNames.size) {
							if(currArg > minArgs) {
								"("++currentMethod.argNames[currArg]++")";
							} {
								currentMethod.argNames[currArg];
							}
						} {
							"(arg"++currArg++")" // excessive arg
						};
					};
				} {
					stream << if(currentMethod.isNil or: {currArg < currentMethod.argNames.size}) {
						currentMethod !? {
							f = currentMethod.argNames[currArg].asString;
							if(
								(z = if(currentMethod.varArgs and: {currArg==(currentMethod.argNames.size-1)})
										{"... "++f} {f}
								) != node.text;
							) {
								"SCDoc: In %\n"
								"  Method %% has arg named '%', but doc has 'argument:: %'.".format(
									currDoc.fullPath,
									if(currentMethod.ownerClass.isMetaClass) {"*"} {"-"},
									currentMethod.name,
									z,
									node.text,
								).warn;
							};
						};
						if(currArg > minArgs) {
							"("++node.text++")";
						} {
							node.text;
						};
					} {
						"("++node.text++")" // excessive arg
					};
				};
				stream << "' - ";
				this.renderChildren(stream, node);
			},
			\RETURNS, {
				stream << "\nRETURNS~\n\n";
				this.renderChildren(stream, node);
				stream << "\n";

			},
			\DISCUSSION, {
				stream << "\nDISCUSSION~\n\n";
				this.renderChildren(stream, node);
			},
// Sections
			\CLASSMETHODS, {
				if(node.notPrivOnly) {
					stream << "\n\nCLASS METHODS~\n\n";
				};
				this.renderChildren(stream, node);
			},
			\INSTANCEMETHODS, {
				if(node.notPrivOnly) {
					stream << "\nINSTANCE METHODS~\n\n";
				};
				this.renderChildren(stream, node);
			},
			\DESCRIPTION, {
				stream << "DESCRIPTION~\n";
				this.renderChildren(stream, node);
			},
			\EXAMPLES, {
				stream << "\nEXAMPLES~\n\n";
				this.renderChildren(stream, node);
			},
			\SECTION, {
				stream << node.text
				<< this.escapeSpecialChars(node.text) << "\n";
				if(node.makeDiv.isNil) {
					this.renderChildren(stream, node);
				} {
					stream << node.makeDiv << "\n";
					this.renderChildren(stream, node);
					stream << "\n";
				};
			},
			\SUBSECTION, {
				stream << "\n\n" << node.text << "~\n\n";
                this.renderChildren(stream, node);
				// if(node.makeDiv.isNil) {
				// 	this.renderChildren(stream, node);
				// } {
				// 	stream << node.makeDiv << "\n";
				// 	this.renderChildren(stream, node);
				// 	stream << "\n";
				// };
			},
			{
				"SCDoc: In %\n"
				"  Unknown SCDocNode id: %".format(currDoc.fullPath, node.id).warn;
				this.renderChildren(stream, node);
			}
		);
	}

	*addUndocumentedMethods {|list, body, id2, id, title|
		var l;
		if(list.size>0) {
			l = list.collectAs(_.asString,Array).sort.collect {|name|
				SCDocNode()
				.id_(id2)
				.children_([
					SCDocNode()
					.id_(\METHODNAMES)
					.children_([
						SCDocNode()
						.id_(\STRING)
						.text_(name.asString)
					])
				]);
			};
			body.addDivAfter(id, nil, title, l);
		}
	}

	*renderClassTree {|stream, cls|
		var name, doc, desc = "";
		name = cls.name.asString;
		doc = SCDoc.documents["Classes/"++name];
		doc !? { desc = " - "++doc.summary };
		if(cls.name.isMetaClassName, {^this});
		stream << "<li> <a href='" << baseDir << "/Classes/" << name << ".txt'>"
		<< name << "</a>" << desc << "\n";

		cls.subclasses !? {
			stream << "<ul class='tree'>\n";
			cls.subclasses.copy.sort {|a,b| a.name < b.name}.do {|x|
				this.renderClassTree(stream, x);
			};
			stream << "</ul>\n";
		};
	}

	*renderFootNotes {|stream|
		if(footNotes.notNil) {
			stream << "<div class='footnotes'>\n";
			footNotes.do {|n,i|
				stream << "<a class='anchor' name='footnote_" << (i+1) << "'/><div class='footnote'>"
				<< "[<a href='#footnote_org_" << (i+1) << "'>" << (i+1) << "</a>] - ";
				noParBreak = true;
				this.renderChildren(stream, n);
				stream << "</div>";
			};
			stream << "</div>";
		};
	}

	*renderFooter {|stream, doc|
		stream << "\n\n vim:tw=78:et:ft=help.supercollider:norl:\n";
	}

	*renderOnStream {|stream, doc, root|
		var body = root.children[1];
		var redirect;
		currDoc = doc;
		footNotes = nil;
		noParBreak = false;

		if(doc.isClassDoc) {
			currentClass = doc.klass;
			currentImplClass = doc.implKlass;
			if(currentClass != Object) {
                // TODO: override addDivAfter
				// body.addDivAfter(\CLASSMETHODS,"inheritedclassmets","Inherited class methods");
				// body.addDivAfter(\INSTANCEMETHODS,"inheritedinstmets","Inherited instance methods");
			};
			this.addUndocumentedMethods(doc.undoccmethods, body, \CMETHOD, \CLASSMETHODS, "Undocumented class methods");
			this.addUndocumentedMethods(doc.undocimethods, body, \IMETHOD, \INSTANCEMETHODS, "Undocumented instance methods");
			body.sortClassDoc;
		} {
			currentClass = nil;
			currentImplClass = nil;
		};

		this.renderHeader(stream, doc, body);
		this.renderChildren(stream, body);
		// this.renderFootNotes(stream);
		this.renderFooter(stream, doc);
		currDoc = nil;
	}

	*renderToFile {|filename, doc, root|
		var stream;
		File.mkdir(filename.dirname);
		stream = File(filename, "w");
		if(stream.isOpen) {
			this.renderOnStream(stream, doc, root);
			stream.close;
		} {
			warn("SCDoc: Could not open file % for writing".format(filename));
		}
	}
}
