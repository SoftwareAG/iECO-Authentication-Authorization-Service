/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 Software GmbH, Darmstadt, Germany and/or its subsidiaries and/or its affiliates
 * SPDX-License-Identifier: Apache-2.0
 */

package eu.xfsc.aas.model.response;

import lombok.Data;

import java.util.List;

@Data
public class SchemaResponse {

    @Data
    public static class Schema {
        private String ver;
        private String id;
        private String name;
        private String version;
        private List<String> attrNames;
        private int seqNo;
    }

    private Schema schema;
}
