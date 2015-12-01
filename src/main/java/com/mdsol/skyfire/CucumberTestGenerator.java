package com.mdsol.skyfire;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.uml2.uml.Region;
import org.eclipse.uml2.uml.StateMachine;
import org.eclipse.uml2.uml.Transition;

import coverage.graph.InvalidGraphException;
import coverage.web.InvalidInputException;

/**
 * A class that generates Cucumber tests
 * 
 * @author Nan Li
 * @version 1.0 Nov 19, 2015
 *
 */
public class CucumberTestGenerator {

    private List<? extends Test> tests;
    private static Logger logger = LogManager.getLogger("CucumberTestGenerator");

    /**
     * Constructs a test with no parameters
     */
    public CucumberTestGenerator(List<? extends Test> tests) {
	this.tests = tests;
    }

    public List<? extends Test> getTests() {
	return tests;
    }

    public void setTests(List<? extends Test> tests) {
	this.tests = tests;
    }

    /**
     * Parses the test and extract identifiable elements from the model Each test is a scenario
     */
    StringBuffer generateScenarios(String featureDescription) {
	StringBuffer sb = new StringBuffer();
	sb.append("Feature: ");
	sb.append(featureDescription + "\n");
	sb.append("\n");

	for (Test test : tests) {
	    if (test instanceof FsmTest) {
		sb.append("Scenario: " + test.getTestComment() + "\n");
		boolean firstWhen = true;
		if (((FsmTest) test).getPath() != null) {
		    for (Transition transition : ((FsmTest) test).getPath()) {
			if (transition != null && transition.getName() != null && transition.getName().indexOf("initialize") >= 0) {
			    sb.append("Given " + transition.getName() + "\n");
			} else if (transition != null && transition.getName() != null && firstWhen) {
			    sb.append("When " + transition.getName() + "\n");
			    firstWhen = false;
			} else if (transition != null && transition.getName() != null && !firstWhen) {
			    sb.append("And " + transition.getName() + "\n");
			} else {
			    continue;
			}
		    }
		} else {
		    logger.debug(test.getTestName() + " does not have paths");
		}
		sb.append("\n\n");
	    }
	}

	return sb;
    }

    /**
     * Parses the test and extract identifiable elements from the model Each test is a scenario
     * 
     * @param featureDescription
     *            the description of the feature file
     * @param tests
     *            a list of tests
     */
    private static StringBuffer generateScenarios(String featureDescription, List<? extends Test> tests) {
	StringBuffer sb = new StringBuffer();
	sb.append("Feature: ");
	sb.append(featureDescription + "\n");
	sb.append("\n");

	for (Test test : tests) {
	    if (test instanceof FsmTest) {
		sb.append("Scenario: " + test.getTestComment() + "\n");
		boolean firstWhen = true;
		if (((FsmTest) test).getPath() != null) {
		    for (Transition transition : ((FsmTest) test).getPath()) {
			if (transition != null && transition.getName() != null && transition.getName().indexOf("initialize") >= 0) {
			    sb.append("Given " + transition.getName() + "\n");
			} else if (transition != null && transition.getName() != null && firstWhen) {
			    sb.append("When " + transition.getName() + "\n");
			    firstWhen = false;
			} else if (transition != null && transition.getName() != null && !firstWhen) {
			    sb.append("And " + transition.getName() + "\n");
			} else {
			    continue;
			}
		    }
		} else {
		    logger.debug(test.getTestName() + " does not have paths");
		}
		sb.append("\n\n");
	    }
	}

	return sb;
    }

    /**
     * Creates a feature file
     * 
     * @param sb
     *            the content of the feature file
     * @param path
     *            the path of the Feature file
     * @throws IOException
     */
    public static void writeFeatureFile(StringBuffer sb, String path) throws IOException {
	String result = sb.toString();

	BufferedWriter bufferWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path), "UTF-8"));
	bufferWriter.write(result);
	bufferWriter.close();
    }

    /**
     * Generates the Cucumber feature file from a UML behavioral model
     * 
     * @param umlPath
     *            the path of the UML model
     * @param coverage
     *            the graph coverage that the tests are going to satisfy
     * @param featureDescription
     *            the description of the feature file to be generated
     * @param featureFilePath
     *            the path of the feature file
     * @return true if the feature file is successfully generated; otherwise return false
     * @throws IOException
     * @throws InvalidGraphException
     * @throws InvalidInputException
     */
    public static boolean generateCucumberScenario(Path umlPath, TestCoverageCriteria coverage, String featureDescription, Path featureFilePath) {
	boolean isGenerated = true;

	// read the UML model
	EObject object = null;
	try {
	    object = StateMachineAccessor.getModelObject(umlPath.toString());
	} catch (IOException e) {
	    logger.debug("Have difficulty in finding the specified UML model");
	    e.printStackTrace();
	}
	List<StateMachine> statemachines = StateMachineAccessor.getStateMachines(object);
	List<Region> regions = StateMachineAccessor.getRegions(statemachines.get(0));
	StateMachineAccessor stateMachine = new StateMachineAccessor(regions.get(0));
	logger.info("Read the specified UML model from " + umlPath.toString());

	// generate the abstract test paths on the flattened graph
	List<coverage.graph.Path> paths = null;
	try {
	    paths = AbstractTestGenerator.getTestPaths(stateMachine.getEdges(), stateMachine.getInitialStates(), stateMachine.getFinalStates(), coverage);
	} catch (InvalidInputException | InvalidGraphException e) {
	    logger.debug("The flattened graph is not valid");
	    e.printStackTrace();
	}
	logger.info("Generate abstract test paths on the flattened graph");

	// find the matched transitions on the original UML model and construct
	// tests
	List<com.mdsol.skyfire.FsmTest> tests = new ArrayList<com.mdsol.skyfire.FsmTest>();

	for (int i = 0; i < paths.size(); i++) {
	    System.out.println("path: " + paths.get(i));
	    List<Transition> transitions = AbstractTestGenerator.convertVerticesToTransitions(AbstractTestGenerator.getPathByState(paths.get(i), stateMachine), stateMachine);

	    String pathName = "";
	    for (Transition transition : transitions) {
		pathName += (transition.getName() != null ? transition.getName() : "") + " ";
	    }
	    com.mdsol.skyfire.FsmTest test = new com.mdsol.skyfire.FsmTest(String.valueOf(i), pathName, transitions);
	    tests.add(test);
	    System.out.println(test.getTestComment());
	}
	logger.info("Generate abstract tests");

	// write the scenarios into the feature file
	StringBuffer sb = generateScenarios(featureDescription, tests);

	try {
	    writeFeatureFile(sb, featureFilePath.toString());
	} catch (IOException e) {
	    logger.debug("Cannot write scenarios into the feature file");
	    e.printStackTrace();
	}
	logger.info("Create Cucumber feature file which is located at " + featureFilePath.toString());

	return isGenerated;
    }
}