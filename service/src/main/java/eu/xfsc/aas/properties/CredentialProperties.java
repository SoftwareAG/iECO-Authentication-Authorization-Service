/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 Software GmbH, Darmstadt, Germany and/or its subsidiaries and/or its affiliates
 * SPDX-License-Identifier: Apache-2.0
 */
package eu.xfsc.aas.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

@Data
@Component
@ConfigurationProperties(prefix = "aas.credential")
public class CredentialProperties {

    private String schemaId;

    private String credentialDefId;

    private Map<String, String> standardClaimMappings;

    /*
     * Remove unmapped claims and reverse the mapping
     */
    public Map<String, String> getFilteredInverseMappings() {
        return standardClaimMappings.entrySet()
            .stream()
            .filter(e -> !((String) e.getValue()).isEmpty())
            .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
    }

}
