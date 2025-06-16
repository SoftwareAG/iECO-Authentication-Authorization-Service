/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 Software GmbH, Darmstadt, Germany and/or its subsidiaries and/or its affiliates
 * SPDX-License-Identifier: Apache-2.0
 */
package eu.xfsc.aas.client;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;

import java.nio.file.Files;
import java.nio.file.Path;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.FileWriter;
import java.util.Optional;
import java.util.Map;
import java.net.URI;

import org.json.JSONException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class AcapyClient{

  private final String HOST;
  private final int PORT;
  private final String PATH;
  private final Path CONNECTION_RECORD;

  public AcapyClient(String host, int port, String path, Path connectionRecord){
    HOST = host;
    PORT = port;
    PATH = path;
    CONNECTION_RECORD = connectionRecord;
    log.debug("Initialized acapyClient with host: {}, port: {}, path: {}", HOST,PORT,PATH);
  }

  public void setAcapyConnection(String APIEndpoint_Key, String acapyConnectionID_Value) throws JSONException, IOException{
    log.info("Saving SSI Connection for "+APIEndpoint_Key);
    JSONObject acapyConnRecord = new JSONObject();

    //Create record if it does not exist
    if(Files.notExists(CONNECTION_RECORD)){
      log.info("Creating connection record");
      Files.createFile(CONNECTION_RECORD);
    }
    else{
      acapyConnRecord = new JSONObject(Files.readString(CONNECTION_RECORD));
    }

    //Write
    acapyConnRecord.put(APIEndpoint_Key, acapyConnectionID_Value);

    FileWriter fw = new FileWriter(CONNECTION_RECORD.toString());
    fw.write(acapyConnRecord.toString());
    fw.close();
  }

  public String getAcapyConnection(String clientEDC) throws JSONException, IOException{
    try{
      JSONObject acapyConnRecord = new JSONObject(Files.readString(CONNECTION_RECORD));
      return acapyConnRecord.get(clientEDC).toString();
    }
    catch(JSONException | NoSuchFileException e){
      log.info("Could not find corresponding SSI Connection for: " + clientEDC);
      return "";
    }
  }

  public boolean hasActiveConnection(String APIEndpoint) throws JSONException, IOException{
    var connection = new JSONObject(acapyGet("/connections/"+getAcapyConnection(APIEndpoint)).readEntity(String.class));
    if(connection.get("state").equals("active")){
        return true;
    }
    else{
      return false;
    }
  }

  public String getPendingPresentationExchange(String APIEndpoint, String state) throws JSONException, IOException{
    var exchanges = acapyGet("/present-proof/records", Optional.ofNullable(Map.of("connection_id", getAcapyConnection(APIEndpoint), "state", state))).readEntity(String.class);
    //log.info("Got: "+exchanges);
    var presEx = new JSONObject(exchanges).getJSONArray("results");
    if(presEx.length() > 0){
      var presExID = presEx.getJSONObject(0).get("presentation_exchange_id").toString();
      return presExID;
    }
    else{
      log.info("No pending presentation exchange found for state: "+state);
      return "";
    }
  }

  public boolean hasVerifiedPresentationExchange(String consumerAPIEndpoint) throws JSONException, IOException{
    boolean isVerified = false;
    var exchanges = new JSONObject(acapyGet("/present-proof/records", Optional.ofNullable(Map.of("connection_id", getAcapyConnection(consumerAPIEndpoint), "state", "verified", "role", "verifier"))).readEntity(String.class)).getJSONArray("results");
    for(int i=0;i<exchanges.length();i++){
      if(exchanges.getJSONObject(i).get("verified").equals("true")){
        isVerified = true;
        break;
      }
    }
    return isVerified;
  }

  public Response acapyPost(String endpoint, JSONObject body) throws JSONException{
    return acapyPost(endpoint, body, Optional.empty());
  }

  public Response acapyPost(String endpoint, JSONObject body, Optional<Map<String,Object>> queryParam){
    //Optional Map<String,String> for queryParams
    UriBuilder baseUri = UriBuilder.fromUri("https://{host}"+ PATH + endpoint)
      .host(HOST);

    if(queryParam.isPresent()){
      Map<String,Object> params = queryParam.get();
      for(var param : params.entrySet()){
        baseUri = baseUri.queryParam(param.getKey(), param.getValue());
      }
    }
    URI uri = baseUri.build(HOST);

    Client client = ClientBuilder.newClient();
    WebTarget target = client.target(uri);

    log.debug("Calling uri: {}", uri.toString());

    return target.request().post(Entity.json(body.toString()));
  }

  public Response acapyGet(String endpoint) throws JSONException{
    return acapyGet(endpoint, Optional.empty());
  }

  public Response acapyGet(String endpoint, Optional<Map<String,String>> queryParam) throws JSONException{
    //Optional Map<String,String> for queryParams
    UriBuilder baseUri = UriBuilder.fromUri("https://{host}"+ PATH + endpoint)
      .host(HOST);

    if(queryParam.isPresent()){
      Map<String,String> params = queryParam.get();
      for(var param : params.entrySet()){
        baseUri = baseUri.queryParam(param.getKey(), param.getValue());
      }
    }
    URI uri = baseUri.build(HOST);

    Client client = ClientBuilder.newClient();
    WebTarget target = client.target(uri);

    log.debug("Calling uri: {}", uri.toString());

    return target.request().get();
  }
}
