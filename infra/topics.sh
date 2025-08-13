#!/bin/bash

# Kafka Topics Creation Script for Event Saga Architecture
# This script creates all necessary topics for the event-driven microservices system
# Usage: ./topics.sh

set -e

# Configuration
KAFKA_CONTAINER="infra-kafka-1"
PARTITIONS=6
REPLICATION_FACTOR=1
RETENTION_MS=604800000  # 7 days
MIN_INSYNC_REPLICAS=1
CLEANUP_POLICY="delete"
COMPRESSION_TYPE="producer"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Helper function to check if topic exists
exists_topic() {
    local topic_name=$1
    docker exec $KAFKA_CONTAINER kafka-topics --bootstrap-server localhost:9092 --list | grep -q "^${topic_name}$"
}

# Helper function to create a topic with standard configurations
create_topic() {
    local topic_name=$1
    local additional_configs=${2:-""}

    if exists_topic "$topic_name"; then
        echo -e "${YELLOW}Topic '$topic_name' already exists, skipping...${NC}"
        return 0
    fi

    echo -e "${GREEN}Creating topic: $topic_name${NC}"

    local config_string="cleanup.policy=${CLEANUP_POLICY},min.insync.replicas=${MIN_INSYNC_REPLICAS},retention.ms=${RETENTION_MS},compression.type=${COMPRESSION_TYPE}"

    if [[ -n "$additional_configs" ]]; then
        config_string="${config_string},${additional_configs}"
    fi

    docker exec $KAFKA_CONTAINER kafka-topics \
        --bootstrap-server localhost:9092 \
        --create \
        --topic "$topic_name" \
        --partitions $PARTITIONS \
        --replication-factor $REPLICATION_FACTOR \
        --config "$config_string"

    echo -e "${GREEN}✓ Topic '$topic_name' created successfully${NC}"
}

# Function to wait for Kafka to be ready
wait_for_kafka() {
    echo "Waiting for Kafka to be ready..."
    local max_attempts=30
    local attempt=1

    while [ $attempt -le $max_attempts ]; do
        if docker exec $KAFKA_CONTAINER kafka-topics --bootstrap-server localhost:9092 --list &>/dev/null; then
            echo -e "${GREEN}✓ Kafka is ready${NC}"
            return 0
        fi
        echo "Attempt $attempt/$max_attempts - Kafka not ready yet, waiting 2 seconds..."
        sleep 2
        ((attempt++))
    done

    echo -e "${RED}✗ Kafka failed to become ready after $max_attempts attempts${NC}"
    exit 1
}

# Main execution
main() {
    echo "================================================"
    echo "      Kafka Topics Creation Script"
    echo "================================================"

    # Wait for Kafka to be ready
    wait_for_kafka

    echo ""
    echo "Creating command topics (events)..."
    echo "-----------------------------------"

    # Command topics (events)
    create_topic "order.created"
    create_topic "payment.requested"
    create_topic "payment.authorized"
    create_topic "payment.failed"
    create_topic "inventory.reserved"
    create_topic "inventory.rejected"
    create_topic "shipping.scheduled"
    create_topic "order.completed"
    create_topic "order.cancelled"

    echo ""
    echo "Creating retry and DLT topics..."
    echo "--------------------------------"

    # Retry and DLQ topics (per domain)
    # Note: Delayed retries will be handled with headers and scheduled consumers
    create_topic "payment.requested-retry"
    create_topic "payment.requested-dlt"
    create_topic "inventory.reserve-retry"
    create_topic "inventory.reserve-dlt"
    create_topic "shipping.schedule-retry"
    create_topic "shipping.schedule-dlt"

    echo ""
    echo "Creating Kafka Streams topics..."
    echo "-------------------------------"

    # Views/state changelog and repartition topics (Kafka Streams)
    create_topic "order-views-store-changelog"
    create_topic "order-views-repartition"

    echo ""
    echo "================================================"
    echo "✓ All topics created successfully!"
    echo "================================================"

    echo ""
    echo "Topic Summary:"
    echo "-------------"
    docker exec $KAFKA_CONTAINER kafka-topics --bootstrap-server localhost:9092 --list | sort

    echo ""
    echo "Retry/DLT Topics Note:"
    echo "• Delayed retries are handled with message headers"
    echo "• Scheduled consumers process retry topics at intervals"
    echo "• DLT (Dead Letter Topic) stores permanently failed messages"
}

# Script entry point
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi
