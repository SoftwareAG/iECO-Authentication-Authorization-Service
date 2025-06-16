/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 Software GmbH, Darmstadt, Germany and/or its subsidiaries and/or its affiliates
 * SPDX-License-Identifier: Apache-2.0
 */

package eu.xfsc.aas.client;

import eu.xfsc.aas.model.response.SchemaResponse;
import eu.xfsc.aas.properties.CredentialProperties;
import eu.xfsc.aas.service.OCMService;
import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static eu.xfsc.aas.generated.model.AccessRequestStatusDto.*;
import static eu.xfsc.aas.model.TrustServicePolicy.*;

@Slf4j
public class DemoTrustServiceClientImpl implements TrustServiceClient {

    private final WebClient ocmHostClient;

    private OCMService ocmService;

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE_REF = new ParameterizedTypeReference<>() {};

    private final String oidcIssuer;

    private CredentialProperties credentialProperties;

    private List<String> schemaAttributeNames;
    
    public DemoTrustServiceClientImpl(CredentialProperties credentialProperties,
                                      String oidcIssuer,
                                      String url,
                                      String ocmHost,
                                      String ocmEndpoint,
                                      int ocmPort) {
        this.oidcIssuer = oidcIssuer;
        this.credentialProperties = credentialProperties;
        ocmHostClient = WebClient.builder().baseUrl(ocmHost.startsWith("https://") ? ocmHost : "https://" + ocmHost)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.debug("OCMService with url: {}, host: {}, port: {}, endpoint: {}", url, ocmHost, ocmPort ,ocmEndpoint);
        //for some reason args are not read from application.yml?? will fix later
        ocmService = new OCMService(ocmHost, ocmPort, ocmEndpoint);
    }

    @PostConstruct()
    public void init() {
        SchemaResponse response = this.ocmHostClient.get()
                .uri("/schemas/" + this.credentialProperties.getSchemaId())
                .retrieve()
                .onStatus(HttpStatusCode::isError, res ->
                        res.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new RuntimeException(
                                        "Failed getting schema with schema id "
                                        + this.credentialProperties.getSchemaId() + "Server responded with status code"
                                        + res.statusCode() + " and body: " + body)))
                )
                .bodyToMono(SchemaResponse.class)
                .retry(3)
                .block();


        if (response == null) {
            throw new RuntimeException("Found null response for requested schema.");
        }
        this.schemaAttributeNames = response.getSchema().getAttrNames();
    }
    
    @Override
    public Map<String, Object> evaluate(String policy, Map<String, Object> params){
        Map<String, Object> map = new HashMap<>();
        String requestId = (String) params.get(PN_REQUEST_ID);
        if (requestId == null && (GET_LOGIN_PROOF_INVITATION.equals(policy) || GET_LOGIN_PROOF_RESULT.equals(policy))) {
            requestId = (String) params.get(IdTokenClaimNames.SUB);
        }
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        map.put(PN_REQUEST_ID, requestId);

        if (GET_IAT_PROOF_INVITATION.equals(policy)) {
            map.put(PN_STATUS, PENDING);
            return map;
        }

        if (GET_LOGIN_PROOF_INVITATION.equals(policy)) {
            log.debug("evaluate; policy: {}, trying to create ocm invite, calling {}", policy, "/ocm/invite");
            Response resp = null;
            //Create Invite
            try{
                resp = ocmService.getInvitation(requestId);
            } catch(Exception e){log.debug("Exception {}", e);}

            int code = resp.getStatus();
            log.debug("evaluate; policy: {}, status code {}", policy, code );
            map.put(PN_LINK, resp.getEntity().toString());
            return map;
        }

        if (GET_LOGIN_PROOF_RESULT.equals(policy) || GET_IAT_PROOF_RESULT.equals(policy)) {
            Response resp = null;
            //Poll Proof Request status from OCM here?
            if (!connectionEstablished(requestId)) {
                map.put(PN_STATUS, PENDING);
            } else {
                try{
                    resp = ocmService.getProofRequestStatus(requestId);
                } catch(Exception e){log.debug("Exception {}", e);}
                String result = new JSONObject(resp.getEntity().toString()).get("state").toString();
                int code = resp.getStatus();
                log.debug("evaluate; policy: {}, result: {} status code {}", policy, result, code);
                switch (result) {
                    case "uninitialized":
                        //Generate your proof Request according to policies here
                        //Demo will send PR from a local file
                        try{
                            buildAndSendDemoProofRequest(requestId, this.schemaAttributeNames, this.credentialProperties.getCredentialDefId());
                        } catch(IOException e){
                            log.error("Error when handeling IO: {}", e);
                        }
                        map.put(PN_STATUS, PENDING);
                        break;
                    case "pending":
                        map.put(PN_STATUS, PENDING);
                        break;
                    case "verified":
                        map.put(PN_STATUS, ACCEPTED);
                        break;
                    case "rejected":    
                        map.put(PN_STATUS, REJECTED);
                        break;
                    default:
                        map.put(PN_STATUS, PENDING);
                }
                try {
                    Response myresp = ocmService.getPresentation(requestId);
                    //log.debug("Got resp Entity: {}", myresp.getEntity());
                    Map<String, String> attributesMap = new HashMap<>();
                    String entityResp =  (String) myresp.getEntity();
                    log.debug("entityResp: {}",entityResp);
                    JSONObject responseJsonObj = new JSONObject(entityResp);
                    log.debug("Get FirstName: {}",responseJsonObj.getString("FirstName"));
                    log.debug("Got Presentation: {}", ocmService.getPresentation(requestId));
                    if (map.get(PN_STATUS) == ACCEPTED && GET_LOGIN_PROOF_RESULT.equals(policy)) {
                        long stamp = System.currentTimeMillis();
                        map.put(IdTokenClaimNames.AUTH_TIME, Instant.now().getEpochSecond());
                        map.put(StandardClaimNames.UPDATED_AT, Instant.now().minusSeconds(86400).getEpochSecond());

                        Map<String, String> inversedStandardClaimMappings = this.credentialProperties.getFilteredInverseMappings();
                        for (String attributeName : this.schemaAttributeNames) {
                            if (!responseJsonObj.has(attributeName)) {
                                continue;
                            }
                            if (inversedStandardClaimMappings.containsKey(attributeName)) {
                                map.put(inversedStandardClaimMappings.get(attributeName), responseJsonObj.getString(attributeName));
                                continue;
                            }

                            map.put(attributeName, responseJsonObj.getString(attributeName));
                        }
                        if (map.containsKey(StandardClaimNames.EMAIL) && !map.containsKey(StandardClaimNames.EMAIL_VERIFIED)) {
                            map.put(StandardClaimNames.EMAIL_VERIFIED, Boolean.TRUE);
                        }
                        /*
                        String hashstring = responseJsonObj.getString("E-Mail") + responseJsonObj.getString("Birthday") +  responseJsonObj.getString("Name") + responseJsonObj.getString("FirstName");
                        MessageDigest digest = MessageDigest.getInstance("SHA-256");
                        byte[] hash = digest.digest(hashstring.getBytes(StandardCharsets.UTF_8));
                        String sha256hex = DatatypeConverter.printHexBinary(hash);
                        log.debug("Hash: {}", sha256hex); 
                        */
                        map.put(IdTokenClaimNames.SUB, "urn:id:" + responseJsonObj.getString("DID") );
                        //map.put(IdTokenClaimNames.SUB, "urn:id:" + responseJsonObj.getString("E-Mail"));
                    }
                }catch(Exception e){log.debug("Exception {}", e);}


            }
            //map.put(IdTokenClaimNames.SUB, "urn:id:" + "requestId");
            map.put(IdTokenClaimNames.ISS, oidcIssuer);
        }

        log.debug("evaluate.exit; policy: {}, params: {}, result: {} ", policy, params, map);
        return map;
    }

    private synchronized boolean connectionEstablished(String requestId) {
        Response resp = null;
        try{
            resp = ocmService.checkConnectionStatus(requestId);
        } catch(Exception e){log.debug("Exception {}", e);}
     
        int code = resp.getStatus();
        log.debug("connectionEstablished; status code {}", code);

        if(code == 200){
            return true;
        }
        else{
            return false;
        }
    }

    private void buildAndSendDemoProofRequest(String requestId, List<String> attributes, String credentialDefId) throws JSONException, IOException {
      Response resp = null;
      log.debug("Initiating demo Proof Request");

      String attributeObjectTemplate = "{\"name\":\"%s\", \"restrictions\":[{\"cred_def_id\":\"%s\"}]}";

      JSONObject requestedAttributesJSON = new JSONObject();
      for (int idx = 1; idx <= attributes.size(); idx++) {
          String key = "attribute" + idx;
          requestedAttributesJSON.put(key, new JSONObject(String.format(attributeObjectTemplate, attributes.get(idx), credentialDefId)));
      }

      JSONObject internalProofRequestJSON = new JSONObject();
      internalProofRequestJSON.put("name", "Proof request for Person");
      internalProofRequestJSON.put("version", "1.0");
      internalProofRequestJSON.put("requested_attributes", requestedAttributesJSON);
      internalProofRequestJSON.put("requested_predicates", new JSONObject());

      /*Construct usable proof request
      ----------------------------------*/
      JSONObject pReqJSON = new JSONObject();
      pReqJSON.put("comment", "Proof request for Person");
      pReqJSON.put("proof_request", internalProofRequestJSON);
      //Set connection_id
      try{
            resp = ocmService.checkConnectionStatus(requestId);
      } catch(Exception e){log.debug("Exception {}", e);}

      String connID = new JSONObject(resp.getEntity().toString()).get("connection_id").toString();
      log.debug("got connection_id: {}", connID);

      /*Construct usable proof request
      ----------------------------------*/
      pReqJSON.put("connection_id", connID);

      //Generate UUID4 for id (only needed for JSON-LD credentials)
      /*pReqJSON.getJSONObject("presentation_request")
        .getJSONObject("dif")
          .getJSONObject("presentation_definition")
            .put("id", UUID.randomUUID().toString());*/

      log.debug("Constructed following proofRequest: /n" + pReqJSON.toString());
      

      //Send Request
      resp = ocmService.sendProofRequest(pReqJSON, requestId);
      String result = resp.getEntity().toString();
      int code = resp.getStatus();
      if(code != 201){
        log.error("Initiation of Proof Request failed, error: {}", result);
      }
    }
}
