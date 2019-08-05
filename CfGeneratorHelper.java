package it.enel.msa.utility.service.helper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.nttdata.integration.Status;

import it.enel.msa.common.exceptions.BusinessLayerException;
import it.enel.msa.common.exceptions.EnelExceptionEnum;
import it.enel.msa.common.util.EnelDateUtils;
import it.enel.msa.utility.common.Constants;
import it.enel.msa.utility.model.cfgenerator.CfGeneratorInputData;
import it.enel.msa.utility.model.cfgenerator.CfGeneratorOutput;

@Component
public class CfGeneratorHelper extends UtilityHelper {
	private Logger log = LoggerFactory.getLogger(CfGeneratorHelper.class);
	@Autowired
	private EnelDateUtils enelDateUtils;
	
	private DateTimeFormatter formatterdMuuuu = DateTimeFormatter.ofPattern(Constants.CFGENERATOR_DATE_FORMATTER).withResolverStyle(ResolverStyle.STRICT);
	
	private Map<String,String> mapChar = new HashMap<>();
	// Map for correspondence between month and character
	private final Map<Integer, String> mapMonth = new HashMap<>();
	// array for generate control code on even characters into fiscal code string
	private static final char[] LIST_EVEN = {'0','1','2','3','4','5','6','7','8','9','A','B',
	                                    'C','D','E','F','G','H','I','J','K','L','M','N',
	                                    'O','P','Q','R','S','T','U','V','W','X','Y','Z'
	                                };
	                                   
	// array for generate control code on odd characters into fiscal code string
	private static final int[] LIST_ODD= { 1, 0, 5, 7, 9, 13, 15, 17, 19, 21, 1, 0, 5, 7, 9, 13,
	                                15, 17, 19, 21, 2, 4, 18, 20, 11, 3, 6, 8, 12, 14, 16,
	                                10, 22, 25, 24, 23
	                               };
	@PostConstruct
	public void init(){
		insertCharMap();
		insertMonthMap();
	}
	
	public CfGeneratorOutput processOutputData(CfGeneratorInputData inputData) throws BusinessLayerException{
		inputData.setName(StringEscapeUtils.UNESCAPE_HTML4.translate(inputData.getName()));
		inputData.setSurname(StringEscapeUtils.UNESCAPE_HTML4.translate(inputData.getSurname()));
		log.info("Process with InputData: {}", inputData);
		CfGeneratorOutput outputData = new CfGeneratorOutput();
		String codiceFiscale = calculateFiscaleCode(inputData);
		outputData.setCf(codiceFiscale);
		log.info("CodiceFiscale: [{}]", codiceFiscale);
		return outputData;
	}
	
	// calculate fiscale code  
	private String calculateFiscaleCode(CfGeneratorInputData inputData) throws BusinessLayerException{
		LocalDate birthdate = getBirthday(inputData.getBirthday());
		String sex = handleSex(inputData.getGender());
	    String cfStr = getSurname(inputData.getSurname()).toUpperCase() + 
		    		getName(inputData.getName()).toUpperCase() + 
		    		getYear(birthdate.getYear()) +
		    		getMonth(birthdate.getMonthValue()) +
		    		getDay(birthdate.getDayOfMonth(), sex) +
		    		inputData.getCadCode();
	    log.info("CodiceFiscale without code: [{}]", cfStr);
	    int even = 0;
	    int odd = 0;
	    int control=0;
	    
	    if (cfStr.length()==15) {
	    	for(int i=0; i < cfStr.length(); i++) {
	    		char ch = cfStr.charAt(i);              // i-th letter of the string
	      
	    		// the first character is one, not zero
	    		if((i+1) % 2 == 0) {  
	    			// for even charaters
	    			int index = Arrays.binarySearch(LIST_EVEN,ch);
	    			even += (index >= 10) ? index-10 : index;
	    		} else {
	    			// for odd characters
	    			int index = Arrays.binarySearch(LIST_EVEN,ch);
	    			odd += LIST_ODD[index];
	    		}
	    	}
	    
	    	control = (even+odd) % 26;
	    	control += 10;
	    } else {
	    	log.info("CodiceFiscale is invalid, length > 15");
	    	throw new BusinessLayerException(Status.of(
	    			EnelExceptionEnum.FAILED.getCode(), 
	    			EnelExceptionEnum.FAILED.getResult(), 
	    			EnelExceptionEnum.FAILED.getDescription()));
	    }
	    return cfStr + LIST_EVEN[control];
	}
	
	private String getName(String name) {
	    return updateNC(name,true);
	}
	
	private String getSurname(String surname) {
	    return updateNC(surname,false);
    }
	
	/**
	  * @param firstlastname   name or surname to modify
	  * @param cod             true for name, false for surname
	  * @return newString      string modified into three lower case letters 
	  */
	private String updateNC(String firstlastname, boolean cod) {
	    String newString = "";
	    
	    // replace every special characters
	    firstlastname = firstlastname.toUpperCase();
       for (Map.Entry<String, String> entry : this.mapChar.entrySet()) {
       	firstlastname = firstlastname.replaceAll(entry.getKey(), entry.getValue());
       }
       	
	    firstlastname = firstlastname.toLowerCase();

	    firstlastname = firstlastname.replaceAll(Constants.CFGENERATOR_STR_SPACE, "");           // remove spaces
	    firstlastname = firstlastname.replaceAll(Constants.CFGENERATOR_STR_APOSTROPHE, "");           // remove apostrophe
	    
	    String consonants = getConsonants(firstlastname);      // return consonants and vowels of the string
	    String vowels = getVowels(firstlastname);
	    
	    // several case
	    if(consonants.length() == 3) {               // if firstlastname contains exactly 3 consonants then it is the new string
	      newString = consonants;
	    }
	    // consonants are insufficient and firstlastname has got at least three letters
	    else if((consonants.length() < 3) && (firstlastname.length() >= 3)) {
	      newString = consonants;
	      newString = addVowels(newString, vowels);   // add vowels
	    } 
	                                                     
       // consonants are insufficient and firstlastname has got less than three letters
	    else if((consonants.length() < 3) && (firstlastname.length() < 3)) {
	      newString = consonants;
	      newString += vowels;    // add vowels and X
	      newString = aggiungiX(newString);
	    } 
	    
	    // consonant are exceeded, get 1,2,3 for surname, 0,2,3 for name                                        
	    else if(consonants.length() > 3) {
	      // true for name, false for surname
	      if (!cod) newString = consonants.substring(0,3);
	      else newString = consonants.charAt(0) +""+ consonants.charAt(2) +""+ consonants.charAt(3);
	    }
	    
	    return newString;
	}
	
	// according to "Agenzia delle Entrate" and governament of Italy (http://www.funzionepubblica.gov.it/media/159141/la_tabella.pdf)
	private void insertCharMap() {
		this.mapChar.put("À", "A");
		this.mapChar.put("Á", "A");
		this.mapChar.put("É", "E");
		this.mapChar.put("È", "E");
		this.mapChar.put("Í", "I");
		this.mapChar.put("Ì", "I");
		this.mapChar.put("Í", "I");
		this.mapChar.put("Ó", "O");
		this.mapChar.put("Ò", "O");
		this.mapChar.put("Ú", "U");
		this.mapChar.put("Ù", "U");

		this.mapChar.put("Â", "A");
		this.mapChar.put("Ă", "A");
		this.mapChar.put("Ā", "A");
		this.mapChar.put("Ã", "A");
		this.mapChar.put("Å", "A");
		this.mapChar.put("Ą", "A");
		this.mapChar.put("Ä", "AE");
		this.mapChar.put("Æ", "AE");
		
		this.mapChar.put("Ć", "C");
		this.mapChar.put("Ċ", "C");
		this.mapChar.put("Ĉ", "C");
		this.mapChar.put("Č", "C");
		this.mapChar.put("Ç", "C");
		
		this.mapChar.put("Ď", "D");
		this.mapChar.put("Đ", "D");
		
		this.mapChar.put("Ė", "E");
		this.mapChar.put("Ê", "E");
		this.mapChar.put("Ë", "E");
		this.mapChar.put("Ě", "E");
		this.mapChar.put("Ĕ", "E");
		this.mapChar.put("Ē", "E");
		this.mapChar.put("Ę", "E");

		this.mapChar.put("Ġ", "G");
		this.mapChar.put("Ĝ", "G");
		this.mapChar.put("Ğ", "G");
		this.mapChar.put("Ģ", "G");
		
		this.mapChar.put("Ĥ", "H");
		this.mapChar.put("Ħ", "H");
		
		this.mapChar.put("Î", "I");
		this.mapChar.put("Ï", "I");
		this.mapChar.put("Ï", "I");
		this.mapChar.put("Ĭ", "I");
		this.mapChar.put("Ī", "I");
		this.mapChar.put("Ĩ", "I");
		this.mapChar.put("Į", "I");
		
		this.mapChar.put("Ĵ", "J");
		this.mapChar.put("Ķ", "K");
		
		this.mapChar.put("Ĺ", "L");
		this.mapChar.put("L·", "L");
		this.mapChar.put("Ľ", "L");
		this.mapChar.put("Ļ", "L");
		this.mapChar.put("Ł", "L");
		
		this.mapChar.put("Ń", "N");
		this.mapChar.put("Ň", "N");
		this.mapChar.put("Ñ", "N");
		this.mapChar.put("Ņ", "N");
		this.mapChar.put("ʼn", "N");
		
		this.mapChar.put("Ô", "O");
		this.mapChar.put("Ö", "OE");
		this.mapChar.put("Œ", "OE");
		this.mapChar.put("Ŏ", "O");
		this.mapChar.put("Ō", "O");
		this.mapChar.put("Õ", "O");
		this.mapChar.put("Ő", "O");
		this.mapChar.put("Ø", "OE");
		
		this.mapChar.put("Ŕ", "R");
		this.mapChar.put("Ř", "R");
		this.mapChar.put("Ŗ", "R");
		
		this.mapChar.put("Ś", "S");
		this.mapChar.put("Ŝ", "S");
		this.mapChar.put("Š", "S");
		this.mapChar.put("Ş", "S");
		this.mapChar.put("ß", "SS");
		
		this.mapChar.put("Ť", "T");
		this.mapChar.put("Ţ", "T");
		this.mapChar.put("Þ", "TH");
		this.mapChar.put("Ŧ", "T");
		
		this.mapChar.put("Û", "U");
		this.mapChar.put("Ü", "UE");
		this.mapChar.put("Ŭ", "U");
		this.mapChar.put("Ū", "U");
		this.mapChar.put("Ũ", "U");
		this.mapChar.put("Ů", "U");
		this.mapChar.put("Ų", "U");
		this.mapChar.put("Ű", "U");
		
		this.mapChar.put("Ŵ", "W");
		this.mapChar.put("Ý", "Y");
		this.mapChar.put("Ŷ", "Y");
		this.mapChar.put("Ÿ", "Y");
		
		this.mapChar.put("Ź", "Z");
		this.mapChar.put("Ż", "Z");
		this.mapChar.put("Ž", "Z");
	}
	
	private void insertMonthMap() {
		mapMonth.put(1, "A");
		mapMonth.put(2, "B");
		mapMonth.put(3, "C");
		mapMonth.put(4, "D");
		mapMonth.put(5, "E");
		mapMonth.put(6, "H");
		mapMonth.put(7, "L");
		mapMonth.put(8, "M");
		mapMonth.put(9, "P");
		mapMonth.put(10, "R");
		mapMonth.put(11, "S");
		mapMonth.put(12, "T");
	}
	
	// remove vowels from string	
	private String getConsonants(String word) {
	    word = word.replaceAll(Constants.CFGENERATOR_REGEX_CONSONANTS,"");
	    return word;
	}
	
	// remove consonants from string	  
	private String getVowels(String word) {
	    word = word.replaceAll(Constants.CFGENERATOR_REGEX_VOWELS, "");
	    return word;
	}
	
	// add vowels for string whose length is less than 3
	private String addVowels(String word, String vowels) {
	    int index = 0;
	    StringBuilder sb = new StringBuilder(word);
	    while(sb.length() < 3) {
	    	sb.append(vowels.charAt(index));
	    	index++;
	    }
	    return sb.toString(); 
	}
	
	// add X for string whose length is less than 3
	private String aggiungiX(String word) {
		StringBuilder sb = new StringBuilder(word);
	    while(sb.length() < 3) {
	    	sb.append(Constants.CFGENERATOR_AGGIUNGI_CHAR);
	    }
	    return sb.toString();
	}
	
	private LocalDate getBirthday(String birthday) throws BusinessLayerException{
		LocalDate date = null;
		try {
			date = enelDateUtils.convertToDate(birthday, formatterdMuuuu);
		} catch (Exception e) {
			throw (BusinessLayerException) new BusinessLayerException(
					Status.of(EnelExceptionEnum.FAILED.getCode(), 
					EnelExceptionEnum.FAILED.getResult(), 
					EnelExceptionEnum.FAILED.getDescription()))
			.initCause(e.getCause());
		}
		return date;
	}
	
	private String getYear(int year) {
	    String anno=String.valueOf((year%100));
	    if (anno.length()==1) {
	    	anno = Constants.CFGENERATOR_PREFIX_ZERO.concat(anno);
	    }
	    return anno;
	}
	
	// return code month
	private String getMonth(int monthValue) {
	    return mapMonth.get(monthValue);
	}
	
	private String getDay(int day, String gender) {
		String daystring = "";
		// for female, day is day+40
		day = (Constants.CFGENERATOR_GENDER_SHORTCUT_MALE.equals(gender) ? day : (day + Constants.CFGENERATOR_DAY_ADDED_FEMALE));
		if (day < 10) {
			// add 0 into string of the day for day<10
			daystring = Constants.CFGENERATOR_PREFIX_ZERO + day;
		} else {
			daystring = Integer.toString(day);
		}
	    return daystring;
    }
	
	private String handleSex(String gender) {
		String sex = gender;
		if (Constants.CFGENERATOR_GENDER_SHORTCUT_MALE.equalsIgnoreCase(gender) || Constants.CFGENERATOR_GENDER_MALE.equalsIgnoreCase(gender))
			sex = "M";
		else if (Constants.CFGENERATOR_GENDER_SHORTCUT_FEMALE.equalsIgnoreCase(gender) || Constants.CFGENERATOR_GENDER_FEMALE.equalsIgnoreCase(gender))
			sex = "F";
		return sex;
	}
	
}
