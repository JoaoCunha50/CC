document.addEventListener("DOMContentLoaded", () => {
    // Fetch the JSON file
    fetch('../../../outputs/metrics.json')
        .then(response => {
            if (!response.ok) {
                throw new Error("Network response was not ok " + response.statusText);
            }
            return response.json();
        })
        .then(data => {
            populateTable(data);
        })
        .catch(error => {
            console.error("Error fetching the JSON file:", error);
        });
});

// Lista de métricas
const metricNames = [
    "cpu",        // 0
    "ram",        // 1
    "latency",    // 2
    "bandwidth",  // 3
    "jitter",     // 4
    "packet loss",// 5
    "interface"   // 6
];

function populateTable(data) {
    const tableBody = document.querySelector("#metricsTable tbody");

    // Clear any existing rows
    tableBody.innerHTML = '';

    // Iterate through the agents and their metrics
    Object.keys(data).forEach(agentID => {
        data[agentID].forEach(metric => {
            // Create a new row
            const row = document.createElement("tr");

            // Create and append cells for AgentID, UUID, Metric Type, Output, and Timestamp
            const agentCell = document.createElement("td");
            agentCell.textContent = agentID;
            row.appendChild(agentCell);

            const uuidCell = document.createElement("td");
            uuidCell.textContent = metric.taskUUID;
            row.appendChild(uuidCell);

            // Map taskType to the corresponding metric name
            const typeCell = document.createElement("td");
            typeCell.textContent = metricNames[metric.taskType] || "Unknown"; // Default to "Unknown" if taskType is out of range
            row.appendChild(typeCell);

            const outputCell = document.createElement("td");
            outputCell.textContent = metric.metrics;
            row.appendChild(outputCell);

            // Add a new cell for the timestamp
            const timestampCell = document.createElement("td");
            timestampCell.textContent = metric.timestamp || "N/A"; // Default to "N/A" if timestamp is missing
            row.appendChild(timestampCell);

            // Append the row to the table body
            tableBody.appendChild(row);
        });
    });
}
