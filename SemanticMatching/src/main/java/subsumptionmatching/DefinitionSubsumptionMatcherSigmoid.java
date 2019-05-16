package subsumptionmatching;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.semanticweb.owl.align.Alignment;
import org.semanticweb.owl.align.AlignmentException;
import org.semanticweb.owl.align.AlignmentProcess;
import org.semanticweb.owl.align.AlignmentVisitor;
import org.semanticweb.owl.align.Cell;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import evaluation.general.Evaluator;
import fr.inrialpes.exmo.align.impl.BasicAlignment;
import fr.inrialpes.exmo.align.impl.BasicConfidence;
import fr.inrialpes.exmo.align.impl.ObjectAlignment;
import fr.inrialpes.exmo.align.impl.URIAlignment;
import fr.inrialpes.exmo.align.impl.rel.A5AlgebraRelation;
import fr.inrialpes.exmo.align.impl.renderer.RDFRendererVisitor;
import matchercombination.HarmonySubsumption;
import net.didion.jwnl.JWNLException;
import utilities.OntologyOperations;
import utilities.Sigmoid;
import utilities.WordNet;

public class DefinitionSubsumptionMatcherSigmoid extends ObjectAlignment implements AlignmentProcess {

	OWLOntology sourceOntology;
	OWLOntology targetOntology;

	//these attributes are used to calculate the weight associated with the matcher's confidence value
	double profileScore;
	int slope;
	double rangeMin;
	double rangeMax;

	static Map<String, Set<String>> defMapOnto1 = new HashMap<String, Set<String>>();
	static Map<String, Set<String>> defMapOnto2 = new HashMap<String, Set<String>>();

	public DefinitionSubsumptionMatcherSigmoid(OWLOntology onto1, OWLOntology onto2, double profileScore) {
		this.sourceOntology = onto1;
		this.targetOntology = onto2;
		this.profileScore = profileScore;
	}

	public DefinitionSubsumptionMatcherSigmoid(OWLOntology onto1, OWLOntology onto2, double profileScore, int slope, double rangeMin, double rangeMax) {
		this.sourceOntology = onto1;
		this.targetOntology= onto2;
		this.profileScore = profileScore;
		this.slope = slope;
		this.rangeMin = rangeMin;
		this.rangeMax = rangeMax;

	}

	public static URIAlignment returnDSMAlignment (File ontoFile1, File ontoFile2, double profileScore, int slope, double rangeMin, double rangeMax) throws OWLOntologyCreationException, AlignmentException {

		URIAlignment DSMAlignment = new URIAlignment();

		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		OWLOntology onto1 = manager.loadOntologyFromOntologyDocument(ontoFile1);
		OWLOntology onto2 = manager.loadOntologyFromOntologyDocument(ontoFile2);

		AlignmentProcess a = new DefinitionSubsumptionMatcherSigmoid(onto1, onto2, profileScore, slope, rangeMin, rangeMax);
		a.init(ontoFile1.toURI(), ontoFile2.toURI());
		Properties params = new Properties();
		params.setProperty("", "");
		a.align((Alignment)null, params);	
		BasicAlignment DefinitionSubsumptionMatcherSigmoidAlignment = new BasicAlignment();

		DefinitionSubsumptionMatcherSigmoidAlignment = (BasicAlignment) (a.clone());

		DSMAlignment = DefinitionSubsumptionMatcherSigmoidAlignment.toURIAlignment();

		DSMAlignment.init( onto1.getOntologyID().getOntologyIRI().toURI(), onto2.getOntologyID().getOntologyIRI().toURI(), A5AlgebraRelation.class, BasicConfidence.class );

		return DSMAlignment;

	}

	public void align(Alignment alignment, Properties param) throws AlignmentException {

		try {
			defMapOnto1 = getDefMap(sourceOntology);
		} catch (FileNotFoundException | JWNLException e1) {
			e1.printStackTrace();
		}
		try {
			defMapOnto2 = getDefMap(targetOntology);
		} catch (FileNotFoundException | JWNLException e1) {
			e1.printStackTrace();
		}

		Set<String> sourceDefTerms = null;
		Set<String> targetDefTerms = null;

		int idCounter = 0; 

		try {
			// Match classes
			for ( Object sourceObject: ontology1().getClasses() ) {
				for ( Object targetObject: ontology2().getClasses() ){
					
					idCounter++;

					String source = ontology1().getEntityName(sourceObject).toLowerCase();
					String target = ontology2().getEntityName(targetObject).toLowerCase();

					if (defMapOnto1.containsKey(source)) {
						sourceDefTerms = defMapOnto1.get(source);
					}

					if (defMapOnto2.containsKey(target)) {
						targetDefTerms = defMapOnto2.get(target);
					}

					if ((sourceDefTerms == null || sourceDefTerms.isEmpty()) && (targetDefTerms == null || targetDefTerms.isEmpty())) {
						addAlignCell("DefinitionSubsumptionMatcherSigmoid" +idCounter, sourceObject, targetObject, "!", 0.0);
					}
					
					else if (sourceDefTerms != null && sourceDefTerms.contains(target)) {						
						addAlignCell("DefinitionSubsumptionMatcherSigmoid" +idCounter, sourceObject, targetObject, "&gt;", 
								Sigmoid.weightedSigmoid(slope, 1.0, Sigmoid.transformProfileWeight(profileScore, rangeMin, rangeMax)));
					}
					
					else if (targetDefTerms != null && targetDefTerms.contains(source)) {					
						addAlignCell("DefinitionSubsumptionMatcherSigmoid" +idCounter, sourceObject, targetObject, "&lt;", 
								Sigmoid.weightedSigmoid(slope, 1.0, Sigmoid.transformProfileWeight(profileScore, rangeMin, rangeMax)));
					}
					
					else {
						
						addAlignCell("DefinitionSubsumptionMatcherSigmoid" +idCounter, sourceObject, targetObject, "!", 0.0);
					}

				}

				}

		} catch (Exception e) { e.printStackTrace(); }

	}


	private Map<String, Set<String>> getDefMap(OWLOntology onto) throws FileNotFoundException, JWNLException {

		Map<String, Set<String>> defMap = new HashMap<String, Set<String>>();

		Set<String> allDefs = new HashSet<String>();

		for (OWLClass c : onto.getClassesInSignature()) {
			Set<String> extracts = new HashSet<String>();
			allDefs.addAll(OntologyOperations.getClassDefinitionsFull(onto, c));

			if (OntologyOperations.getClassDefinitionFull(onto, c).contains("such as") 
					|| OntologyOperations.getClassDefinitionFull(onto, c).contains("e.g.") 
					|| OntologyOperations.getClassDefinitionFull(onto, c).contains("for example") 
					|| OntologyOperations.getClassDefinitionFull(onto, c).contains("includes")
					|| OntologyOperations.getClassDefinitionFull(onto, c).contains("including")) {

				extracts = extractLexicoSyntacticPattern(OntologyOperations.getClassDefinitionFull(onto, c));
				defMap.put(c.getIRI().getFragment().toLowerCase(), extracts);
			}

		}

		return defMap;


	}

	public static Set<String> extractLexicoSyntacticPattern (String def) throws FileNotFoundException, JWNLException {
		String extract = null;
		String cut = null;
		String refined = null;

		//System.out.println("Extracting lexico-syntactic pattern from " + def);

		if (def.contains("such as")) {
			extract = def.substring(def.indexOf("such as")+8, def.length());
		} 
		else if (def.contains("e.g.")) {
			extract = def.substring(def.indexOf("e.g.")+5, def.length());
		}
		else if (def.contains("for example")) {
			extract = def.substring(def.indexOf("for example")+12, def.length());
		}
		else if (def.contains("includes")) {
			extract = def.substring(def.indexOf("includes")+9, def.length());
		}
		else if (def.contains("including")) {
			extract = def.substring(def.indexOf("including")+10, def.length());
		}

		if (extract.contains(".")) {
			cut = extract.substring(0, extract.indexOf("."));
			refined = removeStopWords(cut);
		} else {
			refined = removeStopWords(extract);
		}

		String[] extractArray = refined.split(",| or ");

		Set<String> rawDefTerms = new HashSet<String>();

		for (int i = 0; i < extractArray.length; i++) {

			if (extractArray[i].contains(" a ")) {
				rawDefTerms.add(extractArray[i].replace(" a ", "").replaceAll("\\s", ""));
			}		
			else if (extractArray[i].contains(" an ")) {
				rawDefTerms.add(extractArray[i].replace(" an ", "").replaceAll("\\+", ""));
			}			
			else if (extractArray[i].contains(" or ")) {
				rawDefTerms.add(extractArray[i].replace(" or ", "").replaceAll("\\s", ""));
			} 
			else if (extractArray[i].contains(" and ")) {
				rawDefTerms.add(extractArray[i].replace(" and ", "").replaceAll("\\s", ""));
			} 
			else if (extractArray[i].contains(" etc ")) {
				rawDefTerms.add(extractArray[i].replace(" etc ", "").replaceAll("\\s", ""));
			}
			else {
				rawDefTerms.add(extractArray[i].replaceAll("\\s", ""));
			}
		}

		//extract only those terms that exist in wordnet
		Set<String> refinedDefTerms = new HashSet<String>();

		for (String s : rawDefTerms) {
			if (WordNet.containedInWordNet(s)) {
				refinedDefTerms.add(WordNet.getWordNetLemma(s));
			}
		}

		//include synonym terms
		//		Set<String> synonyms = new HashSet<String>();
		//		for (String s : refinedDefTerms) {
		//			synonyms.addAll(WordNet.getAllSynonymSet(s));
		//		}
		//		
		//		refinedDefTerms.addAll(synonyms);

		return refinedDefTerms;
	}

	private static String removeStopWords (String inputString) {

		List<String> stopWordsList = Arrays.asList(
				"a", "an", "are", "as", "at", "be", "but", "by",
				"for", "if", "in", "into", "is", "it",
				"no", "not", "of", "on", "such",
				"that", "the", "their", "then", "there", "these",
				"they", "this", "to", "was", "will", "with"
				);

		String[] words = inputString.split(" ");
		ArrayList<String> wordsList = new ArrayList<String>();
		StringBuffer sb = new StringBuffer();

		for(String word : words)
		{
			String wordCompare = word.toLowerCase();
			if(!stopWordsList.contains(wordCompare))
			{
				wordsList.add(word);
			}
		}

		for (String str : wordsList){
			sb.append(str + " ");
		}

		return sb.toString();
	}

}