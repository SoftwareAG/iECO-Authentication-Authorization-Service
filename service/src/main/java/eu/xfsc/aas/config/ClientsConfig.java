package eu.xfsc.aas.config;

// SPDX-SnippetBegin
// SPDX-SnippetCopyrightText: Copyright (c) 2025 Software GmbH, Darmstadt, Germany and/or its subsidiaries and/or its affiliates
// SPDX-License-Identifier: Apache-2.0
// SPDX-FileContributor: Modified by Software GmbH
import eu.xfsc.aas.properties.CredentialProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
// SPDX-SnippetEnd
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import eu.xfsc.aas.client.InvitationServiceClient;
// SPDX-SnippetBegin
// SPDX-SnippetCopyrightText: Copyright (c) 2025 Software GmbH, Darmstadt, Germany and/or its subsidiaries and/or its affiliates
// SPDX-License-Identifier: Apache-2.0
// SPDX-FileContributor: Modified by Software GmbH
import eu.xfsc.aas.client.DemoTrustServiceClientImpl;
import eu.xfsc.aas.client.OCMInvitationServiceClientImpl;
// SPDX-SnippetEnd
import eu.xfsc.aas.client.RestInvitationServiceClientImpl;
import eu.xfsc.aas.client.RestTrustServiceClientImpl;
import eu.xfsc.aas.client.TrustServiceClient;
import eu.xfsc.aas.properties.StatusProperties;

@Configuration
public class ClientsConfig {

    // SPDX-SnippetBegin
    // SPDX-SnippetCopyrightText: Copyright (c) 2025 Software GmbH, Darmstadt, Germany and/or its subsidiaries and/or its affiliates
    // SPDX-License-Identifier: Apache-2.0
    // SPDX-FileContributor: Modified by Software GmbH
    @Autowired
    private CredentialProperties credentialProperties;

    @Value("${aas.oidc.issuer}")
    private String oidcIssuer;

    //In this demo's case "controller/OCMController" serves as a bulit-in makeshift TSA inside the AAS
    @Value("${aas.tsa.url}")
    private String url;

    @Value("${aas.tsa.ocm-host}")
    private String ocmHost;

    @Value("${aas.tsa.ocm-endpoint}")
    private String ocmEndpoint;

    @Value("${aas.tsa.ocm-port}")
    private int ocmPort;
    // SPDX-SnippetEnd

    @Bean
    @Profile("prod")
    public TrustServiceClient restTrustServiceClient() {
        return new RestTrustServiceClientImpl();
    }

    @Bean
    @Profile("!prod")
    public TrustServiceClient localTrustServiceClient(StatusProperties statusProperties) {
        // SPDX-SnippetBegin
        // SPDX-SnippetCopyrightText: Copyright (c) 2025 Software GmbH, Darmstadt, Germany and/or its subsidiaries and/or its affiliates
        // SPDX-License-Identifier: Apache-2.0
        // SPDX-FileContributor: Modified by Software GmbH
        return new DemoTrustServiceClientImpl(credentialProperties, oidcIssuer, url, ocmHost, ocmEndpoint, ocmPort);
        // SPDX-SnippetEnd
    }

    @Bean
    @Profile("prod")
    public InvitationServiceClient restInvitationServiceClient() {
        return new RestInvitationServiceClientImpl();
    }

    @Bean
    @Profile("!prod")
    public InvitationServiceClient localInvitationServiceClient(StatusProperties statusProperties) {
        // SPDX-SnippetBegin
        // SPDX-SnippetCopyrightText: Copyright (c) 2025 Software GmbH, Darmstadt, Germany and/or its subsidiaries and/or its affiliates
        // SPDX-License-Identifier: Apache-2.0
        // SPDX-FileContributor: Modified by Software GmbH
        return new OCMInvitationServiceClientImpl();
        // SPDX-SnippetEnd
    }
    
}
