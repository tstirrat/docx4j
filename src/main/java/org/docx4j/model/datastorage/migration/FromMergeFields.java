package org.docx4j.model.datastorage.migration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.xml.bind.JAXBElement;

import org.apache.log4j.Logger;
import org.docx4j.TraversalUtil;
import org.docx4j.TraversalUtil.CallbackImpl;
import org.docx4j.XmlUtils;
import org.docx4j.customXmlProperties.DatastoreItem;
import org.docx4j.customXmlProperties.SchemaRefs;
import org.docx4j.customXmlProperties.SchemaRefs.SchemaRef;
import org.docx4j.jaxb.Context;
import org.docx4j.model.fields.ComplexFieldLocator;
import org.docx4j.model.fields.FieldRef;
import org.docx4j.model.fields.FieldsPreprocessor;
import org.docx4j.model.fields.merge.DataFieldName;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.exceptions.InvalidFormatException;
import org.docx4j.openpackaging.io.SaveToZipFile;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.CustomXmlDataStoragePropertiesPart;
import org.docx4j.openpackaging.parts.PartName;
import org.docx4j.openpackaging.parts.opendope.ConditionsPart;
import org.docx4j.openpackaging.parts.opendope.JaxbCustomXmlDataStoragePart;
import org.docx4j.openpackaging.parts.opendope.QuestionsPart;
import org.docx4j.openpackaging.parts.opendope.StandardisedAnswersPart;
import org.docx4j.openpackaging.parts.opendope.XPathsPart;
import org.docx4j.openpackaging.parts.relationships.RelationshipsPart.AddPartBehaviour;
import org.docx4j.wml.CTDataBinding;
import org.docx4j.wml.CTSdtContentRun;
import org.docx4j.wml.ContentAccessor;
import org.docx4j.wml.P;
import org.docx4j.wml.R;
import org.docx4j.wml.RPr;
import org.docx4j.wml.SdtPr;
import org.docx4j.wml.SdtPr.Alias;
import org.docx4j.wml.SdtRun;
import org.docx4j.wml.Tag;
import org.docx4j.wml.Text;
import org.opendope.answers.Answer;
import org.opendope.answers.Answers;
import org.opendope.conditions.Conditions;
import org.opendope.questions.Question;
import org.opendope.questions.Questionnaire;
import org.opendope.questions.Response;
import org.opendope.xpaths.Xpaths;
import org.opendope.xpaths.Xpaths.Xpath.DataBinding;

/**
 * This class will help you to migrate
 * from MERGEFIELDs
 * to use of content control data bindings.
 * 
 * After migrating, you'll be able to
 * use the OpenDoPE authoring tool to
 * add repeats, conditionals, and other
 * OpenDoPE features, if you need them.
 * 
 * Limitations: this first version
 * operates only on the main document part
 * (ie it won't process variables in
 *  headers/footers, footnotes/endnotes,
 *  or comments)
 *  
 * @author jharrop
 * @since 3.0.0
 */
public class FromMergeFields extends AbstractMigrator {
	
	private static Logger log = Logger.getLogger(FromMergeFields.class);
	
	public WordprocessingMLPackage migrate(WordprocessingMLPackage pkgIn) throws Exception {
		
		// TODO - test that OpenDoPE parts aren't already present
		// or if they are, that this docx is using our answer format
		// (since only that format is supported here)
		
		// Clone it
		WordprocessingMLPackage pkgOut = (WordprocessingMLPackage)pkgIn.clone();
				
		// Create the CustomXML parts
		createParts(pkgOut);
		
		FieldsPreprocessor.complexifyFields(pkgOut.getMainDocumentPart() );
		System.out.println("complexified: " 
				+ XmlUtils.marshaltoString(pkgOut.getMainDocumentPart().getJaxbElement(), true));
		
		// find fields
		ComplexFieldLocator fl = new ComplexFieldLocator();
		new TraversalUtil(pkgOut.getMainDocumentPart().getContent(), fl);
		log.info("Found " + fl.getStarts().size() + " fields ");
		
		
		// canonicalise and setup fieldRefs 
		List<FieldRef> fieldRefs = new ArrayList<FieldRef>();
		for( P p : fl.getStarts() ) {
			int index = ((ContentAccessor)p.getParent()).getContent().indexOf(p);
			P newP = FieldsPreprocessor.canonicalise(p, fieldRefs);
			((ContentAccessor)p.getParent()).getContent().set(index, newP);
			
			/*
			 *   <w:p xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:ns23="http://schemas.openxmlformats.org/schemaLibrary/2006/main" xmlns:m="http://schemas.openxmlformats.org/officeDocument/2006/math">
				    <w:r>
				      <w:t xml:space="preserve">Hallo, lower </w:t>
				    </w:r>
				    <w:r>
				      <w:rPr>
				        <w:noProof/>
				      </w:rPr>
				      <w:fldChar w:fldCharType="begin"/>
				      <w:instrText xml:space="preserve"> MERGEFIELD  kundenname  \* MERGEFORMAT  </w:instrText>
				      <w:fldChar w:fldCharType="separate"/>
				    </w:r>
				    <w:r>
				      <w:rPr>
				        <w:noProof/>
				      </w:rPr>
				      <w:t>«Kundenname»</w:t>
				    </w:r>
				    <w:r/>
				    <w:r>
				      <w:fldChar w:fldCharType="end"/>
				    </w:r>
				  </w:p>
			 */
		}
		
		// Populate
		for (FieldRef fr : fieldRefs) {
			
			String instr = fr.getInstr();
			if ( isMergeField(instr) ) {

				// eg <w:instrText xml:space="preserve"> MERGEFIELD  Kundenstrasse \* MERGEFORMAT </w:instrText>
				// or <w:instrText xml:space="preserve"> MERGEFIELD  Kundenstrasse</w:instrText>
				
				String tmp = instr.substring( instr.indexOf("MERGEFIELD") + 10);
				tmp = tmp.trim();
				String key  = tmp.indexOf(" ") >-1 ? tmp.substring(0, tmp.indexOf(" ")) : tmp ;
				log.info("Key: '" + key + "'");
								
				// Remove the field related runs
				int end = fr.getParent().getContent().indexOf(fr.getEndRun());
				int begin = fr.getParent().getContent().indexOf(fr.getBeginRun());
				for (int i = end; i>=begin; i--) {
					fr.getParent().getContent().remove(i);
				}
				
				// Now add a content control
				List<Object> replacementContent = new ArrayList<Object>();
				createContentControl(null, replacementContent, key);	
				
				fr.getParent().getContent().addAll(begin, replacementContent);
				
//				System.out.println(XmlUtils.marshaltoString(
//						fr.getParent(), true, true));
				
			}
		}
				
		return pkgOut;
	}
	
	public static boolean isMergeField(String type) {
		
		if (type.contains("MERGEFIELD")) {
			return true;
		} else {
			return false;
		}
	}
	


	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {

		String inputfilepath = System.getProperty("user.dir") 
									+ "/TEST1.docx";

		String outputfilepath = System.getProperty("user.dir")
				+ "/OUT_TEST1.docx";

		WordprocessingMLPackage pkgIn = WordprocessingMLPackage.load(new java.io.File(inputfilepath));
		
		FromMergeFields migrator = new FromMergeFields();
		WordprocessingMLPackage pkgOut = migrator.migrate(pkgIn);
		
		SaveToZipFile saver = new SaveToZipFile(pkgOut);
		saver.save(outputfilepath);
		
	}

}
