// Caminho para os ícones
const iconPaths = {
  icon1: "../assets/redeBoa.png", // Ícone para 0-2 alertas
  icon2: "../assets/redeMed.png", // Ícone para 2-6 alertas
  icon3: "../assets/redeMa.png",  // Ícone para mais de 6 alertas
};

// Função para determinar o ícone com base no número de alertas
function getIconPath(alertCount) {
  if (alertCount >= 0 && alertCount < 2) {
    return iconPaths.icon1; // Ícone para 0 a 1 alerta
  } else if (alertCount >= 2 && alertCount <= 6) {
    return iconPaths.icon2; // Ícone para 2 a 6 alertas
  } else {
    return iconPaths.icon3; // Ícone para mais de 6 alertas
  }
}

// Função para calcular a média de métricas
function calculateAverage(metrics) {
  const total = metrics.reduce((sum, metric) => sum + metric, 0);
  return metrics.length > 0 ? (total / metrics.length).toFixed(2) : 'NA';
}

// Fetch para `alerts.json`
fetch('../../../outputs/alerts.json')
  .then(response => response.json())
  .then(alertData => {
    // Fetch para `metrics.json`
    fetch('../../../outputs/metrics.json')
      .then(response => response.json())
      .then(agentData => {
        const container = document.getElementById('agentContainer');

        // Função para criar cartões de agentes dinamicamente
        function createAgentCards(agentData) {
          for (const agent in agentData) {
            const metrics = agentData[agent];
            const taskCount = metrics.length;

            // Nome do agente formatado (ex: "agent1" -> "Agent 1")
            const agentName = agent.replace(/^([a-zA-Z]+)(\d+)$/, (match, p1, p2) => `Agent ${p2}`);

            // Obter número de alertas do `alerts.json` para este agente
            const alertCount = alertData[agent]?.length || 0;

            console.log(`Agent: ${agent}, Task Count: ${taskCount}, Alert Count: ${alertCount}`);
            
            // Determinar o ícone correspondente com base no número de alertas
            const iconPath = getIconPath(alertCount);

            // Filtrar métricas por tipo de tarefa
            const cpuMetrics = metrics.filter(item => item.taskType === 0).map(item => item.metrics);
            const ramMetrics = metrics.filter(item => item.taskType === 1).map(item => item.metrics);
            const packetLossMetrics = metrics.filter(item => item.taskType === 2).map(item => item.metrics);
            const latencyMetrics = metrics.filter(item => item.taskType === 3).map(item => item.metrics);
            const bandwidthMetrics = metrics.filter(item => item.taskType === 4).map(item => item.metrics);
            const jitterMetrics = metrics.filter(item => item.taskType === 5).map(item => item.metrics);
            const interfaceMetrics = metrics.filter(item => item.taskType === 6).map(item => item.metrics);

            // Calcular as médias ou exibir "NA" se não houver dados
            const cpuAvg = calculateAverage(cpuMetrics);
            const ramAvg = calculateAverage(ramMetrics);
            const packetLossAvg = calculateAverage(packetLossMetrics);
            const latencyAvg = calculateAverage(latencyMetrics);
            const bandwidthAvg = calculateAverage(bandwidthMetrics);
            const jitterAvg = calculateAverage(jitterMetrics);
            const interfaceAvg = calculateAverage(interfaceMetrics);

            // Função para condicionar a unidade
            const appendUnit = (value, unit) => value !== 'NA' ? `${value} ${unit}` : value;

            // Manipular a exibição de interface separadamente
            const interfaceDisplay = interfaceAvg !== 'NA' ? `${interfaceAvg}/seg` : 'NA';

            // Criar o HTML para o cartão do agente
            const card = `
                <div class="agent-card">
                  <!-- Adicionar ícone no canto superior direito -->
                  <img src="${iconPath}" alt="Status Icon" style="position: absolute; top: 10px; right: 10px; width: 30px; height: 30px;" />

                  <h2>${agentName}</h2>
                  <p><strong>CPU:</strong> ${appendUnit(cpuAvg, '%')}</p>
                  <p><strong>RAM:</strong> ${appendUnit(ramAvg, '%')}</p>
                  <p><strong>Packet Loss:</strong> ${appendUnit(packetLossAvg, '%')}</p>
                  <p><strong>Latency:</strong> ${appendUnit(latencyAvg, 'ms')}</p>
                  <p><strong>Bandwidth:</strong> ${appendUnit(bandwidthAvg, 'Mbps')}</p>
                  <p><strong>Jitter:</strong> ${appendUnit(jitterAvg, 'ms')}</p>
                  <p><strong>Interface:</strong> ${interfaceDisplay}</p>
                </div>
              `;

            // Adicionar o cartão ao container
            container.innerHTML += card;
          }
        }

        // Executar a função para gerar os cartões dinamicamente
        createAgentCards(agentData);
      })
      .catch(error => {
        console.error('Error loading metrics.json:', error);
      });
  })
  .catch(error => {
    console.error('Error loading alerts.json:', error);
  });
