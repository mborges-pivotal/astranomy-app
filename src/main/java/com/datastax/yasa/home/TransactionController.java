package com.datastax.yasa.home;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.pulsar.client.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import com.datastax.astra.sdk.AstraClient;
import com.datastax.stargate.sdk.doc.ApiDocument;
import com.datastax.stargate.sdk.rest.ApiRestClient;
import com.datastax.stargate.sdk.rest.TableClient;
import com.datastax.stargate.sdk.rest.domain.Row;
import com.datastax.stargate.sdk.rest.domain.RowResultPage;
import com.datastax.stargate.sdk.rest.domain.SearchTableQuery;
import com.datastax.yasa.docapi.banking.BankingRepository;
import com.datastax.yasa.docapi.banking.PendingTransaction;
import com.datastax.yasa.docapi.banking.TransactionMapper;
import com.datastax.yasa.docapi.iot.CSV;
import com.datastax.yasa.docapi.iot.IoTRepository;
import com.datastax.yasa.docapi.iot.Power;
import com.datastax.yasa.docapi.person.Person;
import com.datastax.yasa.docapi.person.PersonModel;
import com.datastax.yasa.docapi.person.PersonRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Controller
public class TransactionController {

	
	@Autowired
	private AstraClient astraClient;
	
	@Autowired
	private BankingRepository repository;
	
	@Autowired
	private PersonRepository homeRepository;
	
	private static final String WORKING_KEYSPACE = "enterprise";
    private static final String WORKING_TABLE    = "pendingtransactions_by_correlationid";
    
    private static ApiRestClient clientApiRest;
    
    private static final String SERVICE_URL = "pulsar+ssl://pulsar-aws-useast2.streaming.datastax.com:6651";
    
    private DateTimeFormatter myFormatObj = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	
	
	 @GetMapping("/banktransactions")
	  public String simulateIoT(@ModelAttribute PersonModel personModel, Model model) throws FileNotFoundException, IOException {
	    
		 System.out.println("Banking Transactions Begin ....");
		 
		 clientApiRest = astraClient.apiStargateData();
		 TableClient transactionTable = clientApiRest.keyspace(WORKING_KEYSPACE).table(WORKING_TABLE);
		 
		 int correlationId = 999;
		 int userId = 1000;
		 
//		 LocalDateTime myDateObj = LocalDateTime.now();
		 

//		 String formattedDate = myDateObj.format(myFormatObj);?
		 
		 for(int i=0; i<100; i++) {
			 
			 
			 Map<String, Object> data = new HashMap<>();
		        data.put("transaction_id", UUID.randomUUID().toString());
		        data.put("user_id", Integer.valueOf(userId++));
		        data.put("message", "Sample Transaction");
		        data.put("created_by", "User");
		        data.put("created_dt", LocalDateTime.now().format(myFormatObj));
			 
			 
			 if(i % 10 == 0) {
				 correlationId ++;
			 }
			 data.put("correlation_id", correlationId);
			 
			 if(i < 50) {
				 data.put("status","Pending");
			 }else {
				 data.put("status","Complete");
			 }
			  
			 transactionTable.upsert(data);
			  
		 }
		 
		 System.out.println("Banking Transactionn Complete ");
		  
		 model.addAttribute("transactions", searchTransactions("Pending"));
	        
	      return "home";
	    
	  }
	 
	 
	 @GetMapping("/batch") 	
	  public String simulateBatch(Model model) throws IOException, InterruptedException {
	    
		 activatePulsar();
		 
//		 clientApiRest = astraClient.apiStargateData();
//		 TableClient transactionTable = clientApiRest.keyspace(WORKING_KEYSPACE).table(WORKING_TABLE);
//		 
//		 List<PendingTransaction> list = transactionTable.search(SearchTableQuery.builder()
//         .where("status").isEqualsTo("Pending")
//         .build(), new TransactionMapper()).getResults();
//		 
//		 
//		 for(PendingTransaction pt : list) {
//			 
//			 Map<String, Object> data = new HashMap<>();
//		        data.put("transaction_id", pt.getTransactionId());
//		        data.put("user_id", pt.getUserId());
//		        data.put("message", pt.getMessage());
//		        data.put("created_by", pt.getCreatedBy());
//		        data.put("status", "Complete");
//		        data.put("correlation_id", pt.getCorrelationId());
//		        data.put("created_dt", null);
//				
//		        transactionTable.upsert(data);
//		 }
		 
		 Thread.sleep(2000);
	        
		 model.addAttribute("transactions", searchTransactions("Complete"));
			 
			 return "home";
	    
	  }
	 
	 @GetMapping("/searchtransactions") 	
	  public String searchTransactions(Model model) {
		 
		 model.addAttribute("transactions", searchTransactions("Pending"));
		 
		 return "home";
		 
	 }
	 
	 private List<PendingTransaction> searchTransactions(String value){
		 
		 clientApiRest = astraClient.apiStargateData();
  		 TableClient transactionTable = clientApiRest.keyspace(WORKING_KEYSPACE).table(WORKING_TABLE);
		 List<PendingTransaction> list = transactionTable.search(SearchTableQuery.builder()
	             .where("status").isEqualsTo(value)
	             .build(), new TransactionMapper()).getResults();
		 
		 return list;
		 
	 }
	 
	 private void activatePulsar() throws IOException, InterruptedException {
		 
		 System.out.println("Starting Pulsar Producer ....");
	    	// Create client object
	        PulsarClient client = PulsarClient.builder()
	                .serviceUrl(SERVICE_URL)
	                .authentication(
	                    AuthenticationFactory.token("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJjbGllbnQ7YzY3NmJhNWItNTEwOS00ZjlkLTgzNWYtM2U5YzE5MTJkYjFkO2MzUnlaV0Z0WkdWdGJ3PT0ifQ.iRGG9va_xDuITc57MSUm-FTx_W1XVyNqqd6KSi75-YetP62rFdbmxfUowOEaWuEYzqlHUhZzeQEEfyfVA-ArANYqm9erCxMwVOtgmgHGXnExUUfNpTvvokuYKS3Wx0IAPmIaRMOd8JFwjeczDyaMQsRXPKG954etHLImwHtsJ_abeSy53YY5ENJcfY2Kc5En8RaBX9c9WzBsXpbi2MRQLYOZWyeMJBERQRSactu_d6IUO_G22_7IfZlsngzf8DzF9iSmgxpgwjsrzoihPQWjWTKSYCgWPY01_iRdIxSQJROBOOCywMPHUrecJZwRRc2I1_Kcjzm8-pDuJJ46c-Pf5Q")
	                )
	                .build();

	        // Create producer on a topic
	        Producer<byte[]> producer = client.newProducer()
	                .topic("persistent://streamdemo/default/demotopic")
	                .create();

	        // Send a message to the topic
	        producer.send("Hello Ram".getBytes());
	        
	        System.out.println("Sent the message  ....");

	        //Close the producer
	        producer.close();

	        // Close the client
	        client.close();
	        
	        consumeMessages();
		 
	 }
	 
	 public void consumeMessages() throws IOException, InterruptedException{
	    	
	    	System.out.println("Starting Pulsar Consumer ....");
	    	
	    	clientApiRest = astraClient.apiStargateData();
      		 TableClient transactionTable = clientApiRest.keyspace(WORKING_KEYSPACE).table(WORKING_TABLE);
      		 
	            // Create client object
	            PulsarClient client = PulsarClient.builder()
	                    .serviceUrl(SERVICE_URL)
	                    .authentication(
	                        AuthenticationFactory.token("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJjbGllbnQ7YzY3NmJhNWItNTEwOS00ZjlkLTgzNWYtM2U5YzE5MTJkYjFkO2MzUnlaV0Z0WkdWdGJ3PT0ifQ.iRGG9va_xDuITc57MSUm-FTx_W1XVyNqqd6KSi75-YetP62rFdbmxfUowOEaWuEYzqlHUhZzeQEEfyfVA-ArANYqm9erCxMwVOtgmgHGXnExUUfNpTvvokuYKS3Wx0IAPmIaRMOd8JFwjeczDyaMQsRXPKG954etHLImwHtsJ_abeSy53YY5ENJcfY2Kc5En8RaBX9c9WzBsXpbi2MRQLYOZWyeMJBERQRSactu_d6IUO_G22_7IfZlsngzf8DzF9iSmgxpgwjsrzoihPQWjWTKSYCgWPY01_iRdIxSQJROBOOCywMPHUrecJZwRRc2I1_Kcjzm8-pDuJJ46c-Pf5Q")
	                    )
	                    .build();

	            // Create consumer on a topic with a subscription
	            Consumer consumer = client.newConsumer()
	                    .topic("streamdemo/default/demotopic")
	                    .subscriptionName("my-subscription")
	                    .subscribe();

	            boolean receivedMsg = false;
	            // Loop until a message is received
	            do {
	                // Block for up to 1 second for a message
	                Message msg = consumer.receive(1, TimeUnit.SECONDS);

	                if(msg != null){
	                    System.out.printf("Message received: %s", new String(msg.getData()));

	                    // Acknowledge the message to remove it from the message backlog
	                    consumer.acknowledge(msg);

	                    receivedMsg = true;
	                    
	           		 List<PendingTransaction> list = transactionTable.search(SearchTableQuery.builder()
	                    .where("status").isEqualsTo("Pending")
	                    .build(), new TransactionMapper()).getResults();
	           		 
	           		 System.out.println(" Rows with Status Pending = "+list.size());
	           		 
	           		 for(PendingTransaction pt : list) {
	           			 
	           			 Map<String, Object> data = new HashMap<>();
	           		        data.put("transaction_id", pt.getTransactionId());
	           		        data.put("user_id", pt.getUserId());
	           		        data.put("message", pt.getMessage());
	           		        data.put("created_by", pt.getCreatedBy());
	           		        data.put("status", "Complete");
	           		        data.put("correlation_id", pt.getCorrelationId());
	           		        data.put("created_dt", LocalDateTime.now().format(myFormatObj));
	           				
	           		        transactionTable.upsert(data);
	           		 }
	                }

	            } while (!receivedMsg);
	            
	            

	            //Close the consumer
	            consumer.close();

	            // Close the client
	            client.close();

	    }
	
	
}
