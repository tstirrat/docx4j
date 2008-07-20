/*
 *  Copyright 2007-2008, Plutext Pty Ltd.
 *   
 *  This file is part of docx4j.

    docx4j is licensed under the Apache License, Version 2.0 (the "License"); 
    you may not use this file except in compliance with the License. 

    You may obtain a copy of the License at 

        http://www.apache.org/licenses/LICENSE-2.0 

    Unless required by applicable law or agreed to in writing, software 
    distributed under the License is distributed on an "AS IS" BASIS, 
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
    See the License for the specific language governing permissions and 
    limitations under the License.

 */

package org.docx4j.samples;



import java.io.FileOutputStream;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;

import org.docx4j.jaxb.Context;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;


public class Filter {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {

		String inputfilepath = System.getProperty("user.dir") + "/sample-docs/contentcontrols.docx";
				
		// Do we want to save output? 
		boolean save = false;
		// If so, whereto?
		String outputfilepath = System.getProperty("user.dir") + "/sample-docs/qformat.pkg";		
		
		
		// Open a document from the file system
		WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.load(new java.io.File(inputfilepath));
		
		
		// Apply the filter
		WordprocessingMLPackage.FilterSettings filterSettings = new WordprocessingMLPackage.FilterSettings();
		filterSettings.setRemoveProofErrors(true);
		filterSettings.setRemoveContentControls(true);
		filterSettings.setRemoveRsids(true);
		wordMLPackage.filter(filterSettings);
		
		
	   	// Create a org.docx4j.wml.Package object
    	org.docx4j.wml.Package pkg = wordMLPackage.exportPkgXml();
    	
    	// Now marshall it
		JAXBContext jc = Context.jc;
		Marshaller marshaller=jc.createMarshaller();
		
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		marshaller.setProperty("com.sun.xml.internal.bind.namespacePrefixMapper", 
				new org.docx4j.jaxb.NamespacePrefixMapper() ); // Must use 'internal' for Java 6
		

		
		//org.w3c.dom.Document doc = org.docx4j.XmlUtils.neww3cDomDocument();	
		if (save) {
			marshaller.marshal(pkg, new FileOutputStream(outputfilepath));				
			System.out.println( "\n\n .. written to " + outputfilepath);
		} else {
			// Display its contents 
			System.out.println( "\n\n OUTPUT " );
			System.out.println( "====== \n\n " );	
			marshaller.marshal(pkg, System.out);				
		}
		
		
				
	}
	
	

}