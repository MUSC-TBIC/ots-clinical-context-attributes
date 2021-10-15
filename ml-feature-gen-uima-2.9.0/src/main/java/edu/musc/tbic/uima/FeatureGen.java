package edu.musc.tbic.uima;

import java.io.File;
import java.io.FileWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.factory.AggregateBuilder;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import edu.musc.tbic.readers.FileSystemCollectionReader;

import org.apache.ctakes.core.ae.SimpleSegmentAnnotator;
import org.apache.ctakes.core.ae.SentenceDetector;

import edu.musc.tbic.context.AlAttrFeatureGen;
import edu.musc.tbic.context.ConTextAnnotator;
import edu.musc.tbic.context.FastContextAnnotator;
import edu.musc.tbic.opennlp.OpenNlpTokenizer;

import edu.musc.tbic.writers.XmlWriter;
import edu.utah.bmi.nlp.context.ConText;

/**
 * 
 * 
 * 
 * 
 */

public class FeatureGen extends org.apache.uima.fit.component.JCasAnnotator_ImplBase {

	private static final Logger mLogger = LoggerFactory.getLogger( FeatureGen.class );
	private static Boolean mTestFlag;
	
	private static String mVersion;
    
    static String documentType = null;
    
    static DocumentBuilderFactory mFactory;
    static DocumentBuilder mDomBuilder;

    public static void main(String[] args) throws ResourceInitializationException, UIMAException, IOException {

    	final Properties project_properties = new Properties();
    	final Properties pipeline_properties = new Properties();
    	project_properties.load( FeatureGen.class.getClassLoader().getResourceAsStream( "project.properties" ) );
    	mVersion = project_properties.getProperty( "version" );
    	
    	// create Options object
    	Options options = new Options();
    	// add option
    	options.addOption( "h" , "help" , false , "Display this help screen" );
    	options.addOption( "v" , "version" , false , "Display FeatureGen build version" );
    	// TODO
    	options.addOption( "c" , false , "Display FeatureGen configuration settings" );
    	// TODO
    	options.addOption( "s" , "soft-load" , false , "Soft-load all resources in all modules and report progress" );
    	//
        options.addOption( "p" , "pipeline-properties" , true , "Load the provided pipeline.properties file rather than the default" );
        //
        CommandLineParser parser = new DefaultParser();
    	try {
            CommandLine cmd = parser.parse( options , args );
	        
	        if( cmd.hasOption( "help" ) ){
	            help( options );
	        }   
	        
	        if( cmd.hasOption( "pipeline-properties" ) ){
	            String pipeline_properties_file = cmd.getOptionValue( "pipeline-properties" );
	            mLogger.debug( "Loading non-default pipeline.properties file: " + pipeline_properties_file );
	            pipeline_properties.load( FeatureGen.class.getClassLoader().getResourceAsStream( pipeline_properties_file ) );
	        } else {
	            try {
	                pipeline_properties.load( FeatureGen.class.getClassLoader().getResourceAsStream( "pipeline.properties" ) );
	            } catch( NullPointerException e )
	            {
	                mLogger.error( "Unable to load pipeline.properties file. Copy over the template pipeline.properties.TEMPLATE or use the --pipeline-properties command-line option to specify an alternate file." );
                    help( options );
	            }
	        }
	        
	        if( cmd.hasOption( "version" ) ){
	            System.out.println( "Configured to run resources for FeatureGen Pipeline (FeatureGen) " + mVersion );
	            System.exit(0);
	        } else if( cmd.hasOption( "c" ) ){
	            System.out.println( "Configured to run resources for FeatureGen Pipeline (FeatureGen) " + mVersion + "\n" +
	                    "\nThis option has not yet been implemented." );
	            System.exit(0);
	        } else if( cmd.hasOption( "soft-load" ) ){
	            System.out.println( "Configured to run resources for FeatureGen Pipeline (FeatureGen) " + mVersion + "\n" +
	                    "\nThis option has not yet been implemented." );
	            System.exit(0);
	        }
    	} catch( ParseException exp ) {
    		// oops, something went wrong
    		mLogger.error( "Parsing failed.  Reason: " + exp.getMessage() );
		}

    	mLogger.info( "Loading resources for FeatureGen Pipeline (FeatureGen) " + mVersion );
		
		///////////////////////////////////////////////////
		String pipeline_test_flag = pipeline_properties.getProperty( "fit.test_flag" );
		String pipeline_reader = pipeline_properties.getProperty( "fit.reader" );
		String pipeline_engines = pipeline_properties.getProperty( "fit.engines" );
		String pipeline_writers = pipeline_properties.getProperty( "fit.writers" );
		ArrayList<String> pipeline_modules = new ArrayList<String>();
		/////////////////////
		if( pipeline_test_flag.equalsIgnoreCase( "true" ) ){
			mTestFlag = true;
			mLogger.info( "Test flag set");
		} else if( pipeline_test_flag.equalsIgnoreCase( "false" ) ){
			mTestFlag = false;
		} else {
			mLogger.error( "Unrecognized test_flag value in pipeline.properties file (defaulting to 'true'):  '" + pipeline_test_flag + "'" );
			mTestFlag = true;
			mLogger.info( "Test flag set");
		}
		/////////////////////
		// Nothing special to do here other than make sure it is a valid/known reader
		if( pipeline_reader.equals( "Text Reader" ) ||
            pipeline_reader.equals( "ast Reader" ) ||
            pipeline_reader.equals( "brat Reader" ) ||
            pipeline_reader.equals( "INCEpTION Reader" ) ||
            pipeline_reader.equals( "WebAnno XMI Reader" ) ){
			pipeline_modules.add( pipeline_reader );
		} else {
			mLogger.error( "Unrecognized reader in pipeline.properties file:  '" + pipeline_reader + "'" );
		}
		/////////////////////
		//Nothing special to do here other than make sure it is a valid/known engine
		if( pipeline_engines.trim().equals( "" ) ){
			mLogger.debug( "No analysis engines added from the pipeline.properties file" );
		} else {
			for( String engine: pipeline_engines.split( "," ) ){
				engine = engine.trim();
				if( engine.equals( "cTAKES SBD" ) |
                    engine.equals( "OpenNLP Tokenizer" ) |
                    engine.equals( "ConText" ) |
                    engine.equals( "FastContext" ) |
                    engine.equals( "Context Attributes" ) ){
					pipeline_modules.add( engine );
				} else {
					mLogger.error( "Unrecognized analysis engine in pipeline.properties file:  '" + engine + "'" );
				}
			}
		}
		/////////////////////
		//Nothing special to do here other than make sure it is a valid/known writer
		for( String writer: pipeline_writers.split( "," ) ){
			writer = writer.trim();
			if( writer.equals( "XML Out" ) ){
				pipeline_modules.add( writer );
			} else if( writer.equals( "" ) ) {
			    mLogger.info( "No writer specified in the pipeline.properties file." );
			} else {
				mLogger.error( "Unrecognized writer in pipeline.properties file:  '" + writer + "'" );
			}
		}
	
		///////////////////////////////////////////////////
        CollectionReaderDescription collectionReader = null;
        AggregateBuilder builder = new AggregateBuilder();
        
        ///////////////////////////////////////////////////
        if( pipeline_modules.contains( "Text Reader" ) ){
            ////////////////////////////////////
            // Initialize plain text reader
            String inputDir = "data/input";
            if( pipeline_properties.containsKey( "fs.in.text" ) ){
                inputDir = pipeline_properties.getProperty( "fs.in.text" );
            }
            mLogger.info( "Loading module 'Text Reader' for " + inputDir );
            collectionReader = CollectionReaderFactory.createReaderDescription(
                    FileSystemCollectionReader.class ,
                    FileSystemCollectionReader.PARAM_INPUTDIR , inputDir );
        } else if( pipeline_modules.contains( "WebAnno XMI Reader" ) ){
            ////////////////////////////////////
            // Initialize WebAnno CAS XMI reader
            String inputDir = "data/input";
            if( pipeline_properties.containsKey( "fs.in.xmi" ) ){
                inputDir = pipeline_properties.getProperty( "fs.in.xmi" );
            }
            mLogger.info( "Loading module 'WebAnno XMI Reader' for " + inputDir );
            collectionReader = CollectionReaderFactory.createReaderDescription(
                    FileSystemCollectionReader.class ,
                    FileSystemCollectionReader.PARAM_INPUTDIR , inputDir );
        } else if( pipeline_modules.contains( "INCEpTION Reader" ) ){
            ////////////////////////////////////
            // Initialize INCEpTION CAS XMI reader
            String inputDir = "data/input";
            if( pipeline_properties.containsKey( "fs.in.xmi" ) ){
                inputDir = pipeline_properties.getProperty( "fs.in.xmi" );
            }
            mLogger.info( "Loading module 'INCEpTION Reader' for " + inputDir );
            collectionReader = CollectionReaderFactory.createReaderDescription(
                    FileSystemCollectionReader.class ,
                    FileSystemCollectionReader.PARAM_INPUTDIR , inputDir );
        } else if( pipeline_modules.contains( "brat Reader" ) ){
            ////////////////////////////////////
            // Initialize brat reader
            String inputDir = "data/input";
            String annDir = "data/input";
            if( pipeline_properties.containsKey( "fs.in.text" ) ){
                inputDir = pipeline_properties.getProperty( "fs.in.text" );
            }
            if( pipeline_properties.containsKey( "fs.in.ann" ) ){
                annDir = pipeline_properties.getProperty( "fs.in.ann" );
            }
            mLogger.info( "Loading module 'brat Reader' for " + inputDir );
            collectionReader = CollectionReaderFactory.createReaderDescription(
                    FileSystemCollectionReader.class ,
                    FileSystemCollectionReader.PARAM_INPUTDIR , inputDir ,
                    FileSystemCollectionReader.PARAM_ANNOTDIR , annDir ,
                    FileSystemCollectionReader.PARAM_ANNOTSUFFIX , ".ann" );
        } else if( pipeline_modules.contains( "ast Reader" ) ){
            ////////////////////////////////////
            // Initialize brat reader
            String inputDir = "data/input";
            String astDir = "data/input";
            if( pipeline_properties.containsKey( "fs.in.text" ) ){
                inputDir = pipeline_properties.getProperty( "fs.in.text" );
            }
            if( pipeline_properties.containsKey( "fs.in.ast" ) ){
                astDir = pipeline_properties.getProperty( "fs.in.ast" );
            }
            mLogger.info( "Loading module 'ast Reader' for " + inputDir );
            collectionReader = CollectionReaderFactory.createReaderDescription(
                    FileSystemCollectionReader.class ,
                    FileSystemCollectionReader.PARAM_INPUTDIR , inputDir ,
                    FileSystemCollectionReader.PARAM_ANNOTDIR , astDir ,
                    FileSystemCollectionReader.PARAM_ANNOTSUFFIX , ".ast" );
        }
        
        ///////////////////////////////////////////////////
        // Sentence Splitters
        ///////////////////////////////////////////////////
        String sentence_type = null;
        ////////////////////////////////////
        if( pipeline_modules.contains( "cTAKES SBD" ) ){
            mLogger.info( "Loading cTAKES SentenceDetectorAnnotator" );
            sentence_type = "org.apache.ctakes.typesystem.type.textspan.Sentence";
            AnalysisEngineDescription ctakesSimpleSegments = AnalysisEngineFactory.createEngineDescription(
                    SimpleSegmentAnnotator.class );
            builder.add( ctakesSimpleSegments );
            AnalysisEngineDescription ctakesSentence = AnalysisEngineFactory.createEngineDescription(
                    SentenceDetector.class ,
                    SentenceDetector.PARAM_SD_MODEL_FILE , "ctakesModels/sd-med-model.zip" );
            builder.add( ctakesSentence );
        }
 
        ///////////////////////////////////////////////////
        // Tokenizers
        ///////////////////////////////////////////////////
        String conceptMapper_token_type = null;
        File tmpTokenizerDescription = null;
        if( pipeline_modules.contains( "OpenNLP Tokenizer" ) ){
            mLogger.info( "Loading OpenNLP's en-token and en-pos-maxent models with aggressive patch" );
            conceptMapper_token_type = "org.apache.ctakes.typesystem.type.syntax.BaseToken";
            AnalysisEngineDescription openNlpTokenizer = AnalysisEngineFactory.createEngineDescription(
                    OpenNlpTokenizer.class ,
                    OpenNlpTokenizer.PARAM_MODELPATH , "resources/openNlpModels/" ,
                    OpenNlpTokenizer.PARAM_TOKENIZERMODEL , "en-token.bin" ,
                    OpenNlpTokenizer.PARAM_TOKENIZERPATCH , "aggressive" ,
                    OpenNlpTokenizer.PARAM_POSMODEL , "en-pos-maxent.bin" );
            tmpTokenizerDescription = File.createTempFile("prefix_", "_suffix");
            tmpTokenizerDescription.deleteOnExit();
            try {
                openNlpTokenizer.toXML(new FileWriter(tmpTokenizerDescription));
            } catch (SAXException e) {
                // TODO - add something here
            }
            builder.add( openNlpTokenizer );
        }

        ////////////////////////////////////
        // ConText
        if( pipeline_modules.contains( "ConText" ) ){
            mLogger.info( "Loading module 'ConText'" );
            String context_log_file = "";
            if( pipeline_properties.containsKey( "context.log_file" ) ){
                context_log_file = pipeline_properties.getProperty( "context.log_file" );
            }
            AnalysisEngineDescription conText = AnalysisEngineFactory.createEngineDescription(
                    ConTextAnnotator.class,
                    ConTextAnnotator.PARAM_SENTENCETYPE, sentence_type ,
                    ConText.PARAM_NEGEX_PHRASE_FILE , "resources/ConText_rules.txt" ,
                    ConText.PARAM_CONTEXT_LOG , context_log_file
                    );
            builder.add( conText );
        }

        ///////////////////////////////////////////////////
        // FastContext Annotator
        ///////////////////////////////////////////////////
        if( pipeline_modules.contains( "FastContext" ) ){
            mLogger.info( "Loading module 'FastContext'" );
            String classMap = "";
            if( pipeline_properties.containsKey( "fs.liblinear.classmap" ) ){
                classMap = pipeline_properties.getProperty( "fs.liblinear.classmap" );
            }
            AnalysisEngineDescription fastContext = AnalysisEngineFactory.createEngineDescription(
                    FastContextAnnotator.class,
                    FastContextAnnotator.PARAM_SENTENCETYPE, sentence_type , 
                    FastContextAnnotator.PARAM_CLASSMAP , classMap
                    );
            builder.add( fastContext );
        }
        
        ///////////////////////////////////////////////////
        // Context Attribute Features
        ///////////////////////////////////////////////////
        if( pipeline_modules.contains( "Context Attributes" ) ){
            mLogger.info( "Loading Context Attributes Feature Generator" );
            // Feature output directory and filename
            String outputFeatureDir = "data/test/out/tsv";
            String outputFeatureFilename = "defaultFeatures";
            if( pipeline_properties.containsKey( "fs.out.features.dir" ) ){
                outputFeatureDir = pipeline_properties.getProperty( "fs.out.features.dir" );
            }
            if( pipeline_properties.containsKey( "fs.out.features.file" ) ){
                outputFeatureFilename = pipeline_properties.getProperty( "fs.out.features.file" );
            }
            String modelFile = "";
            String classMap = "";
            String bowMap = "";
            if( pipeline_properties.containsKey( "fs.liblinear.model" ) ){
                modelFile = pipeline_properties.getProperty( "fs.liblinear.model" );
            }
            if( pipeline_properties.containsKey( "fs.liblinear.classmap" ) ){
                classMap = pipeline_properties.getProperty( "fs.liblinear.classmap" );
            }
            if( pipeline_properties.containsKey( "fs.liblinear.bowmap" ) ){
                bowMap = pipeline_properties.getProperty( "fs.liblinear.bowmap" );
            }
            AnalysisEngineDescription alAttrFeatureGen = AnalysisEngineFactory.createEngineDescription(
                    AlAttrFeatureGen.class ,
                    AlAttrFeatureGen.PARAM_OUTPUTDIR , outputFeatureDir ,
                    AlAttrFeatureGen.PARAM_FVFILE , outputFeatureFilename ,
                    AlAttrFeatureGen.PARAM_MODELFILE , modelFile ,
                    AlAttrFeatureGen.PARAM_CLASSMAP , classMap ,
                    AlAttrFeatureGen.PARAM_BOWMAP , bowMap );
            builder.add( alAttrFeatureGen );
        }
        
        ////////////////////////////////////
        // Initialize XMI and tsv writer
        AnalysisEngineDescription xmlWriter = null;
        if( pipeline_modules.contains( "XML Out" ) ){
            // The default values for these output directories are
            // determined by whether it is a test run or a 
            // production run...
            String xml_output_dir = "";
            String xml_error_dir = "";
            if( mTestFlag ){
        	    mLogger.info( "Loading module 'xmlWriter' for test" );
        	    xml_output_dir = "/tmp/featuregen/test_out";
        	    xml_error_dir = "/tmp/featuregen/test_error";
        	} else {
        	    mLogger.info( "Loading module 'xmlWriter' for production" );
                xml_output_dir = "/data/software/featuregen/data/out/v" + mVersion;
                xml_error_dir = "/data/software/featuregen/data/out/error";
        	}
            // ...However, these values are overwritten if set
            // in the pipeline.properties file
            if( pipeline_properties.containsKey( "fs.out.xmi" ) ){
                xml_output_dir = pipeline_properties.getProperty( "fs.out.xmi" );
                mLogger.debug( "Setting XML output directory: " + xml_output_dir );
            }
            if( pipeline_properties.containsKey( "fs.error.xmi" ) ){
                xml_error_dir = pipeline_properties.getProperty( "fs.error.xmi" );
                mLogger.debug( "Setting XML error directory: " + xml_error_dir );
            }
            // Then we use these values to construct our writer
            xmlWriter = AnalysisEngineFactory.createEngineDescription(
                    XmlWriter.class , 
                    XmlWriter.PARAM_OUTPUTDIR , xml_output_dir ,
                    XmlWriter.PARAM_ERRORDIR , xml_error_dir );
            if( ! mTestFlag ){
                builder.add( xmlWriter );
            }
        }
//        if( pipeline_modules.contains( "OMOP CDM Writer" ) ){
//            Boolean write_to_disk_flag = true;
//        	if( mTestFlag ){
//                mLogger.info( "Loading module 'dataMartWriter' for test" );
//        	    write_to_disk_flag = true;
//        	} else {
//                mLogger.info( "Loading module 'dataMartWriter' for production" );
//        	    write_to_disk_flag = false;
//        	}
//            AnalysisEngineDescription dataMartWriter = AnalysisEngineFactory.createEngineDescription(
//                    OMOP_CDM_CASConsumer.class, 
//                    OMOP_CDM_CASConsumer.PARAM_VERSION , mVersion ,
//                    OMOP_CDM_CASConsumer.PARAM_AUTOINCREMENT , false ,
//                    OMOP_CDM_CASConsumer.PARAM_DBCONNECTION , database_properties_filename ,
//                    OMOP_CDM_CASConsumer.PARAM_WRITETODISK , write_to_disk_flag );
//            builder.add( dataMartWriter );
//        }
        // If we're running in test mode, we actually want to run the database
        // writer prior to the XMI writer. This allows the database writer to do
        // any special cleaning and filtering prior to writing out the XMI
        // (usually for evaluation).
        if( pipeline_modules.contains( "XML Out" ) && mTestFlag ){
            builder.add( xmlWriter );
        }

    	SimplePipeline.runPipeline( collectionReader , builder.createAggregateDescription() );
        
    }

    private static void help( Options options ) {
    	// This prints out some help
    	HelpFormatter formater = new HelpFormatter();
    	formater.printHelp( "edu.musc.tbic.uima.FeatureGen" , options );
    		
    	System.exit(0);
    }	
    
    public void process(JCas arg0) throws AnalysisEngineProcessException {
        // TODO Auto-generated method stub
        
    }
}
