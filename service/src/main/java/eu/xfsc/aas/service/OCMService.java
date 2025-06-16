/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 Software GmbH, Darmstadt, Germany and/or its subsidiaries and/or its affiliates
 * SPDX-License-Identifier: Apache-2.0
 */
package eu.xfsc.aas.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import jakarta.ws.rs.core.Response;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.json.JSONArray;
import lombok.extern.slf4j.Slf4j;

import eu.xfsc.aas.client.AcapyClient;

import org.json.JSONException;
import java.io.IOException;

@Slf4j
public class OCMService {


    private AcapyClient acapyClient;

    public OCMService(String host, int port, String endpoint){
      log.debug("OCMService with host: {}, port: {}, endpoint: {}", host, port ,endpoint);
      acapyClient = new AcapyClient(host, port, endpoint, java.nio.file.Path.of("./acapy_connections.json"));
    }


    public Response getInvitation(String requestID) throws JSONException, IOException{
        log.info("Received a invite creation request");

        //Create acapy Invitation
        var resp = acapyClient.acapyPost("/connections/create-invitation", new JSONObject("{}"), Optional.ofNullable(Map.of("alias", "OCM"))).readEntity(String.class);
        var connectionID = new JSONObject(resp).get("connection_id").toString();
        var invite = new JSONObject(resp).get("invitation_url").toString();
        log.debug("got conn_id: {} and invite: {}, persisting...", connectionID, invite);
        //Persist Connection
        acapyClient.setAcapyConnection(requestID, connectionID);

        return Response.ok(invite).build();
    }
    public Response getPresentation(String requestID) throws JSONException, IOException{
        log.info("Initiating getPresentation...");
        String conid = acapyClient.getAcapyConnection(requestID);
        log.debug("connection_id:{}",conid);
        Map<String, String> connectionMap;
        connectionMap = new HashMap<>();
        connectionMap.put("connection_id", conid);
        var presentations = acapyClient.acapyGet("/present-proof/records", Optional.of(connectionMap));
        log.debug("presentations from connection id : {}",presentations);
        //log.debug("presentation entity : {}",presentations.readEntity(String.class));
        JSONObject present = new JSONObject(presentations.readEntity(String.class));

        // Get the "results" array
        JSONArray resultsArray = present.getJSONArray("results");
        // Assuming there's only one item in the "results" array for simplicity
        JSONObject firstResult = resultsArray.getJSONObject(0);
        // Get the "presentation" object
        //JSONObject presentationObject = firstResult.getJSONObject("presentation");

        // Get the "presentation_request" object
        JSONObject presentationRequestObject = (JSONObject) firstResult.get("presentation_request");

        // Get the "requested_attributes" object
        JSONObject requestedAttributesObject = presentationRequestObject.getJSONObject("requested_attributes");
        // Create a map to store attribute names and values
        log.debug("revealedAttrs : {}",requestedAttributesObject);
        Map<String, String> attributesMap = new HashMap<>();
        // Iterate through the keys in the "revealed_attrs" object
        for (String key : requestedAttributesObject.keySet()) {


            String rawKey = requestedAttributesObject.getJSONObject(key)
                    .getString("name");
            String rawValue = firstResult.getJSONObject("presentation")
                    .getJSONObject("requested_proof")
                    .getJSONObject("revealed_attrs")
                    .getJSONObject(key)
                    .getString("raw");
            log.debug("MAP Key : {} Value: {}",key,rawValue);
            // Put the attribute name and "raw" value into the map
            attributesMap.put(rawKey, rawValue);
        }
        // Print the attributes map
        log.debug("Attributes Map: " + attributesMap);
        JSONObject responseObject = new JSONObject(attributesMap);

        //var resp= acapyClient.acapyGet("/present-proof/records/"+presentations.toString());
        //log.debug("Response for record:{}",resp);
        return Response.ok(responseObject.toString()).build();
    }
    public Response checkConnectionStatus(String requestID) throws JSONException, IOException{
        log.info("Checking Connection Status");
        JSONObject connectionStatusDto = new JSONObject();
        connectionStatusDto.put("connection_id", acapyClient.getAcapyConnection(requestID));

        if(acapyClient.hasActiveConnection(requestID)){
          return Response.ok(connectionStatusDto.toString()).build();
        }
        return Response.status(102).entity(connectionStatusDto.toString()).build();
    }

    public Response sendProofRequest(JSONObject proofRequest, String requestID) throws JSONException, IOException{
      log.info("Initiating proof request...");
      var resp = new JSONObject(acapyClient.acapyPost("/present-proof/send-request", proofRequest).readEntity(String.class));
      log.info("Sent proof request");

      try{
        JSONArray proofNotificationDto = resp.getJSONObject("by_format")
                                              .getJSONObject("pres_request")
                                                .getJSONObject("dif")
                                                  .getJSONObject("presentation_definition")
                                                    .getJSONArray("input_descriptors");

        return Response.status(201).entity(proofNotificationDto.toString()).build();
      } catch(JSONException e){
         return Response.status(400).entity(resp.toString()).build();
      }
    }

    public Response getProofRequestStatus(String requestID)  throws JSONException, IOException{
      log.info("Checking Presentation status");
      //Construct Response
      var requestResponse = new JSONObject();
      

      var presExID = acapyClient.getPendingPresentationExchange(requestID, "verified");
      //Check if consumer has already sent a presentation to verify
      if(!presExID.isEmpty()){
        log.info("A presentation was received for request: "+requestID);
        //Uncomment for manual verification
        //var resp = new JSONObject(acapyClient.acapyPost("/present-proof-2.0/records/"+presExID+"/verify-presentation", new JSONObject()).readEntity(String.class));

        var verified = acapyClient.hasVerifiedPresentationExchange(requestID);
        if(verified){
          log.info("Verfication was successful");
          requestResponse.put("state", "verified");
        }
        else{
          log.info("Verfication failed");
          requestResponse.put("state", "rejected");
        }
        return Response.ok(requestResponse.toString()).build();
      }

      presExID = acapyClient.getPendingPresentationExchange(requestID, "request_sent");
      if(presExID.isEmpty()){
        log.info("No presentation has been sent for request: "+requestID);
        requestResponse.put("state", "uninitialized");
        return Response.ok(requestResponse.toString()).build();
      }
      else{
        log.info("No presentation has been received for request: "+requestID);
        requestResponse.put("state", "pending");
      }

      return Response.ok(requestResponse.toString()).build();
    }
}
