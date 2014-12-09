package org.sitenv.vocabularies.engine;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.sitenv.vocabularies.constants.VocabularyConstants;
import org.sitenv.vocabularies.data.DisplayNameValidationResult;
import org.sitenv.vocabularies.loader.Loader;
import org.sitenv.vocabularies.loader.LoaderManager;
import org.sitenv.vocabularies.model.CodeModel;
import org.sitenv.vocabularies.model.VocabularyModelDefinition;
import org.sitenv.vocabularies.repository.VocabularyRepository;
import org.sitenv.vocabularies.watchdog.RepositoryWatchdog;

import com.orientechnologies.orient.object.db.OObjectDatabaseTx;

public abstract class ValidationEngine {
	
	private static Logger logger = Logger.getLogger(ValidationEngine.class);
	private static RepositoryWatchdog watchdog = null;
	
	public static RepositoryWatchdog getWatchdogThread()
	{
		return watchdog;
	}
	
	public static boolean isCodeSystemLoaded(String codeSystem) {
		VocabularyRepository ds = VocabularyRepository.getInstance();
		VocabularyModelDefinition vocabulary = null;
		if (codeSystem != null) {
			Map<String, VocabularyModelDefinition> vocabMap = ds.getVocabularyMap();
			
			if (vocabMap != null) {
				vocabulary = vocabMap.get(codeSystem.toUpperCase());
			}
		}
		
		return (vocabulary != null);
	}
	
	public static DisplayNameValidationResult validateCodeSystem(String codeSystemName, String displayName, String code) {
		String codeSystem = VocabularyConstants.CODE_SYSTEM_MAP.get(codeSystemName.toUpperCase());
		DisplayNameValidationResult result = null;
		
		if (codeSystem != null)
		{
			result = validateDisplayNameForCode(codeSystem, displayName, code);
		}
		
		return result;
	}
	
	public static DisplayNameValidationResult validateDisplayNameForCodeByCodeSystemName(String codeSystemName, String displayName, String code) {
		String codeSystem = VocabularyConstants.CODE_SYSTEM_MAP.get(codeSystemName.toUpperCase());
		DisplayNameValidationResult result = null;
		
		if (codeSystem != null)
		{
			result = validateDisplayNameForCode(codeSystem, displayName, code);
		}
		
		return result;
	}
	
	public static DisplayNameValidationResult validateDisplayNameForCode(String codeSystem, String displayName, String code) {
		VocabularyRepository ds = VocabularyRepository.getInstance();
		
		if (codeSystem != null && code != null &&  ds != null && ds.getVocabularyMap() != null) {
			Map<String, VocabularyModelDefinition> vocabMap = ds.getVocabularyMap();
			
			VocabularyModelDefinition vocab = vocabMap.get(codeSystem.toUpperCase());
			
			List<? extends CodeModel> results = ds.fetchByCode(vocab.getClazz(), code);
			
			for(CodeModel instance : results)
			{
				DisplayNameValidationResult result = new DisplayNameValidationResult();
				result.setCode(code);
				result.setActualDisplayName(instance.getDisplayName());
				result.setAnticipatedDisplayName(displayName);
				if (instance.getDisplayName() != null && instance.getDisplayName().equalsIgnoreCase(displayName))
				{
					result.setResult(true);
				}
				else
				{
					result.setResult(false);
				}
				
				return result;
				
			}
			
		}
		
		return null;
	}
	
	public static boolean validateCodeByCodeSystemName(String codeSystemName, String code)
	{
		String codeSystem = VocabularyConstants.CODE_SYSTEM_MAP.get(codeSystemName.toUpperCase());
		
		if (codeSystem != null)
		{
			return validateCode(codeSystem, code);
		}
		
		return false;
	}

	public static synchronized boolean validateCode(String codeSystem, String code)
	{
		VocabularyRepository ds = VocabularyRepository.getInstance();
		
		if (codeSystem != null && code != null &&  ds != null && ds.getVocabularyMap() != null) {
			Map<String, VocabularyModelDefinition> vocabMap = ds.getVocabularyMap();
			
			VocabularyModelDefinition vocab = vocabMap.get(codeSystem.toUpperCase());
			
			List<? extends CodeModel> results = ds.fetchByCode(vocab.getClazz(), code);
			
			if (results != null && results.size() > 0)
			{
				return true; // instance of code found
			}
			
		}
		
		return false;
	}
	
	public static boolean validateDisplayNameByCodeSystemName(String codeSystemName, String displayName)
	{
		String codeSystem = VocabularyConstants.CODE_SYSTEM_MAP.get(codeSystemName.toUpperCase());
		
		if (codeSystem != null)
		{
			return validateDisplayName(codeSystem, displayName);
		}
		
		return false;
	}
	
	public static synchronized boolean validateDisplayName(String codeSystem, String displayName)
	{
		VocabularyRepository ds = VocabularyRepository.getInstance();
		
		if (codeSystem != null && displayName != null &&  ds != null && ds.getVocabularyMap() != null) {
			Map<String, VocabularyModelDefinition> vocabMap = ds.getVocabularyMap();
			
			VocabularyModelDefinition vocab = vocabMap.get(codeSystem.toUpperCase());
			
			List<? extends CodeModel> results = ds.fetchByDisplayName(vocab.getClazz(), displayName);
			
			if (results != null && results.size() > 0)
			{
				return true; // instance of code found
			}
			
		}
		
		return false;
	}
	
	public static void initialize(String directory) throws IOException {
		boolean recursive = true;

		logger.info("Registering Loaders...");
		// register Loaders
		registerLoaders();
		logger.info("Loaders Registered...");
		
		// Validation Engine should load using the primary database (existing). This will kick off the loading of the secondary database and swap configs
		// Once the secondary dB is loaded, the watchdog thread will be initialized to monitor future changes.
		// Putting this initialization code in a separate thread will dramatically speed up the tomcat launch time
		InitializerThread initializer = new InitializerThread();
		
		initializer.setDirectory(directory);
		initializer.setRecursive(recursive);
		
		initializer.start();
	}
	
	private static void registerLoaders() {
		try {
			Class.forName("org.sitenv.vocabularies.loader.snomed.SnomedLoader");
			Class.forName("org.sitenv.vocabularies.loader.loinc.LoincLoader");
			Class.forName("org.sitenv.vocabularies.loader.rxnorm.RxNormLoader");
			Class.forName("org.sitenv.vocabularies.loader.icd9.Icd9CmDxLoader");
			Class.forName("org.sitenv.vocabularies.loader.icd9.Icd9CmSgLoader");
			Class.forName("org.sitenv.vocabularies.loader.icd10.Icd10CmLoader");
			Class.forName("org.sitenv.vocabularies.loader.icd10.Icd10PcsLoader");
		} catch (ClassNotFoundException e) {
			// TODO: log4j
			logger.error("Error Initializing Loaders", e);
		}
	}
	
	public static void loadDirectory(String directory) throws IOException
	{
		File dir = new File(directory);
		
		if (dir.isFile())
		{
			logger.debug("Directory to Load is a file and not a directory");
			throw new IOException("Directory to Load is a file and not a directory");
		}
		else
		{
			
			File[] list = dir.listFiles();
			
			VocabularyRepository.getInstance().setVocabularyMap(new HashMap<String,VocabularyModelDefinition>());
			
			for (File file : list)
			{
				loadFiles(file);
			}
		}
	}
	
	private static void loadFiles(File directory) throws IOException
	{
		if (directory.isDirectory() && !directory.isHidden()) 
		{
			File[] filesToLoad = directory.listFiles();
			String codeSystem = null;
			
			for (File loadFile : filesToLoad)
			{
				if (loadFile.isFile() && !loadFile.isHidden())
				{
					
					

					
					logger.debug("Building Loader for directory: " + directory.getName() + "...");
					Loader loader = LoaderManager.getInstance().buildLoader(directory.getName());
					if (loader != null) {
						logger.debug("Loader built...");
					
						codeSystem = loader.getCodeSystem();
					
						logger.debug("Loading file: " + loadFile.getAbsolutePath() + "...");
						VocabularyModelDefinition vocab = loader.load(loadFile);
						
						// TODO: Make this a passed in parameter:
						
						VocabularyRepository.getInstance().getVocabularyMap().put(codeSystem.toUpperCase(), vocab);
						
						logger.debug("File loaded...");
					}
					else 
					{
						logger.debug("Building of Loader Failed.");
					}
					
				}
			}
			
		}
		
		

	}
	
	private static class InitializerThread extends Thread {
		
		private String directory = null;
		private boolean recursive = true;
		
		
		
		public String getDirectory() {
			return directory;
		}



		public void setDirectory(String directory) {
			this.directory = directory;
		}



		public boolean isRecursive() {
			return recursive;
		}



		public void setRecursive(boolean recursive) {
			this.recursive = recursive;
		}



		public void run() {
			
			// Get inactive repository (hopefully this opens a new connection)
			OObjectDatabaseTx dbConnection = VocabularyRepository.getInstance().getInactiveDbConnection();
			
			try 
			{
				logger.info("Loading vocabularies at: " + directory + "...");
				loadDirectory(directory);
				logger.info("Vocabularies loaded...");
				
				logger.info("Starting Watchdog...");
				ValidationEngine.watchdog = new RepositoryWatchdog(this.getDirectory(), this.isRecursive());
				watchdog.start();
				logger.info("Watchdog started...");
			}
			catch (Exception e)
			{
				logger.error("Failed to load configured vocabulary directory.", e);
			}
			finally
			{
				dbConnection.close();
			}
			
			// TODO: Perform Validation/Verification, if needed
			
			logger.info("Activating new Vocabularies Map...");
			
			VocabularyRepository.getInstance().toggleActiveDatabase();
			
			Runtime.getRuntime().gc();
			logger.info("New vocabulary Map Activated...");
		}
		
	}

}
