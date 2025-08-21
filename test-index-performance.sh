#!/bin/bash

echo "=== SKIN INDEX PERFORMANCE TEST ==="
echo "Testing skin index caching performance..."
echo

# Clear all existing indexes
echo "Step 1: Clearing all existing indexes..."
find /root/skins -name ".skintool-index.json" -delete
echo "Indexes cleared."
echo

# Run the tester to create indexes and measure time
echo "Step 2: Running initial indexing (cold cache)..."
START=$(date +%s%N)
timeout 10 mvn -q exec:java -Dexec.mainClass="com.osuskin.tool.test.SkinIndexTester" -Dexec.args="/root/skins" 2>/dev/null | grep -E "(TEST|Success|Failed|Summary|loaded in|Speedup)" | head -50
END=$(date +%s%N)
COLD_TIME=$((($END - $START) / 1000000))
echo "Cold cache time: ${COLD_TIME}ms"
echo

# Count created indexes
INDEX_COUNT=$(find /root/skins -name ".skintool-index.json" 2>/dev/null | wc -l)
echo "Created $INDEX_COUNT index files"
echo

# Run again with cached indexes
echo "Step 3: Running with cached indexes (warm cache)..."
START=$(date +%s%N)
timeout 10 mvn -q exec:java -Dexec.mainClass="com.osuskin.tool.test.SkinIndexTester" -Dexec.args="/root/skins" 2>/dev/null | grep -E "(TEST|Success|Failed|Summary|loaded in|Speedup)" | head -50
END=$(date +%s%N)
WARM_TIME=$((($END - $START) / 1000000))
echo "Warm cache time: ${WARM_TIME}ms"
echo

# Calculate speedup
if [ $WARM_TIME -gt 0 ]; then
    SPEEDUP=$(echo "scale=2; $COLD_TIME / $WARM_TIME" | bc)
    echo "Overall speedup: ${SPEEDUP}x"
fi

echo
echo "=== TEST COMPLETE ==="

# Show a few index files
echo
echo "Sample index files created:"
ls -lh /root/skins/*/.skintool-index.json 2>/dev/null | head -5