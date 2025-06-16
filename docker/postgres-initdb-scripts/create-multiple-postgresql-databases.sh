#!/bin/bash

set -e
set -u

function create_user_and_database() {
	local database=$1
	echo "  Creating user and database '$database'"
	# SPDX-SnippetBegin
  # SPDX-SnippetCopyrightText: Copyright (c) 2025 Software GmbH, Darmstadt, Germany and/or its subsidiaries and/or its affiliates
  # SPDX-License-Identifier: Apache-2.0
  # SPDX-FileContributor: Modified by Software GmbH
	psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
		SET client_min_messages TO notice;
	    CREATE DATABASE $database;
	    CREATE USER $database WITH PASSWORD '$database';
	    GRANT ALL PRIVILEGES ON DATABASE $database TO $database;
	    GRANT ALL PRIVILEGES ON DATABASE $database TO $POSTGRES_USER;
		\c $database
		GRANT ALL ON SCHEMA public TO $POSTGRES_USER;
		GRANT ALL ON SCHEMA public TO $database;
		
EOSQL
  # SPDX-SnippetEnd
}

if [ -n "$POSTGRES_MULTIPLE_DATABASES" ]; then
	echo "Multiple database creation requested: $POSTGRES_MULTIPLE_DATABASES"
	for db in $(echo $POSTGRES_MULTIPLE_DATABASES | tr ',' ' '); do
		create_user_and_database $db
	done
	echo "Multiple databases created"
fi
