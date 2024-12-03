// Fetch the JSON data and populate the table
document.addEventListener("DOMContentLoaded", () => {
    const tableBody = document.querySelector("#tasksTable tbody");

    // Fetch data from a JSON file
    fetch("../../tasks.json") // Adjust the path to your JSON file
        .then((response) => {
            if (!response.ok) {
                throw new Error("Failed to fetch JSON file");
            }
            return response.json();
        })
        .then((data) => {
            populateTable(data);
        })
        .catch((error) => {
            console.error("Error fetching JSON:", error);
        });

    // Function to populate the table with JSON data
    function populateTable(data) {
        // Loop through each agent in the JSON
        data.forEach((agent) => {
            const agentId = agent.agent_id || "-";

            // Loop through tasks for each agent
            agent.tasks.forEach((task) => {
                const row = document.createElement("tr");

                // Populate row cells with task data
                const type = task.task_type !== undefined ? task.task_type : "-";
                const frequency = task.frequency !== undefined ? task.frequency : "-";
                const threshold = task.alertflow_condition !== undefined ? task.alertflow_condition : "-";
                const iperfMode = task.mode !== undefined ? task.mode : "-";
                const destinationIP = task.destination_ip !== undefined ? task.destination_ip : "-";
                const interfaceName = task.interfaceName !== undefined ? task.interfaceName : "-";

                // Add cells to the row
                row.innerHTML = `
                    <td>${agentId}</td>
                    <td>${getTaskType(type)}</td>
                    <td>${frequency}</td>
                    <td>${threshold}</td>
                    <td>${iperfMode}</td>
                    <td>${destinationIP}</td>
                    <td>${interfaceName}</td>
                `;

                // Append the row to the table body
                tableBody.appendChild(row);
            });
        });
    }
});


// Helper function to map taskType to its description
function getTaskType(taskType) {
    const types = [
        "cpu",
        "ram",
        "latency",
        "bandwidth",
        "jitter",
        "packet loss",
        "interface",
    ];
    return types[taskType] || "Unknown";
}