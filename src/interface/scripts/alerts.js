// Fetch the alerts.json file and populate the table
document.addEventListener("DOMContentLoaded", function () {
    const tableBody = document.querySelector("#alertTable tbody");
  
    // Fetch the JSON file
    fetch("../../../outputs/alerts.json")
      .then((response) => {
        if (!response.ok) {
          throw new Error(`Failed to fetch alerts.json: ${response.statusText}`);
        }
        return response.json();
      })
      .then((data) => {
        // Iterate over each agent in the JSON file
        Object.keys(data).forEach((agentId) => {
          const alerts = data[agentId];
          alerts.forEach((alert) => {
            // Create a new row for the alert
            const row = document.createElement("tr");
  
            // Populate the row with the alert datadata
            row.innerHTML = `
              <td>${agentId}</td>
              <td>${alert.taskUUID}</td>
              <td>${getTaskType(alert.taskType)}</td>
              <td>${alert.metrics}</td>
              <td>${alert.threshold}</td>
            `;
  
            // Append the row to the table body
            tableBody.appendChild(row);
          });
        });
      })
      .catch((error) => {
        console.error("Error loading alerts.json:", error);
      });
  });
  
  // Helper function to map taskType to its description
  function getTaskType(taskType) {
    const types = [
      "CPU",
      "RAM",
      "LATENCY",
      "BANDWIDTH",
      "JITTER",
      "PACKET LOSS",
      "INTERFACE",
    ];
    return types[taskType] || "Unknown";
  }
  