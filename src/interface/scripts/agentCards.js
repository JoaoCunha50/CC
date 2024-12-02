// Fetch the JSON file
fetch('../../../outputs/metrics.json')
  .then(response => response.json())
  .then(agentData => {
    // Check if agentData is empty
    const container = document.getElementById('agentContainer');


    // Function to calculate the average metrics for each agent
    function calculateAverage(metrics) {
      const total = metrics.reduce((sum, metric) => sum + metric, 0);
      return metrics.length > 0 ? (total / metrics.length).toFixed(2) : 'NA';
    }

    // Function to create agent cards dynamically
    function createAgentCards(agentData) {
      for (const agent in agentData) {
        const metrics = agentData[agent];

        // Convert agent name to "Agent 1" format (e.g., agent1 -> Agent 1)
        const agentName = agent.replace(/^([a-zA-Z]+)(\d+)$/, (match, p1, p2) => `Agent ${p2}`);

        // Filter metrics by task types
        const cpuMetrics = metrics.filter(item => item.taskType === 0).map(item => item.metrics);
        const ramMetrics = metrics.filter(item => item.taskType === 1).map(item => item.metrics);
        const packetLossMetrics = metrics.filter(item => item.taskType === 2).map(item => item.metrics);
        const latencyMetrics = metrics.filter(item => item.taskType === 3).map(item => item.metrics);
        const bandwidthMetrics = metrics.filter(item => item.taskType === 4).map(item => item.metrics);
        const jitterMetrics = metrics.filter(item => item.taskType === 5).map(item => item.metrics);
        const interfaceMetrics = metrics.filter(item => item.taskType === 6).map(item => item.metrics);

        // Calculate the averages or display "NA" if no data exists
        const cpuAvg = calculateAverage(cpuMetrics);
        const ramAvg = calculateAverage(ramMetrics);
        const packetLossAvg = calculateAverage(packetLossMetrics);
        const latencyAvg = calculateAverage(latencyMetrics);
        const bandwidthAvg = calculateAverage(bandwidthMetrics);
        const jitterAvg = calculateAverage(jitterMetrics);
        const interfaceAvg = calculateAverage(interfaceMetrics);

        // Function to conditionally append the unit
        const appendUnit = (value, unit) => value !== 'NA' ? `${value} ${unit}` : value;

        // Handle the interface value separately
        const interfaceDisplay = interfaceAvg !== 'NA' ? `${interfaceAvg}/seg` : 'NA';

        // Create the HTML for each agent card
        const card = `
          <div class="agent-card">
            <h2>${agentName}</h2>
            <p><strong>CPU:</strong> ${appendUnit(cpuAvg, '%')}</p>
            <p><strong>RAM:</strong> ${appendUnit(ramAvg, '%')}</p>
            <p><strong>Packet Loss:</strong> ${appendUnit(packetLossAvg, '%')}</p>
            <p><strong>Latency:</strong> ${appendUnit(latencyAvg, '%')}</p>
            <p><strong>Bandwidth:</strong> ${appendUnit(bandwidthAvg, 'Mbps')}</p>
            <p><strong>Jitter:</strong> ${appendUnit(jitterAvg, 'ms')}</p>
            <p><strong>Interface:</strong> ${interfaceDisplay}</p>
          </div>
        `;

        // Insert the card into the container
        container.innerHTML += card;
      }
    }

    // Run the function to generate cards dynamically
    createAgentCards(agentData);
  })
  .catch(error => {
    console.error('Error loading JSON data:', error);
  });
