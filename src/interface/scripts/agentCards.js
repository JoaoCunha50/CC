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

// Inicialização do script
fetch('../../../outputs/alerts.json')
  .then(response => {
    if (!response.ok) {
      console.warn('alerts.json não encontrado. Continuando sem alertas.');
      return {};
    }
    return response.json();
  })
  .catch(error => {
    console.warn('Erro ao carregar alerts.json:', error);
    return {};
  })
  .then(alertData => {
    // Carrega metrics.json independentemente do status de alerts.json
    fetch('../../../outputs/metrics.json')
      .then(response => {
        if (!response.ok) {
          console.warn(`Falha ao carregar metrics.json: ${response.statusText}`);
          return {};
        }
        return response.json();
      })
      .catch(error => {
        console.warn('Erro ao carregar metrics.json:', error);
        return {};
      })
      .then(metricData => {
        const container = document.getElementById('agentContainer');

        // Combinar agentes de alerts.json e metrics.json
        const allAgents = new Set([
          ...Object.keys(alertData),
          ...Object.keys(metricData),
        ]);

        // Função para criar cartões de agentes dinamicamente
        function createAgentCards() {
          allAgents.forEach(agent => {
            const metrics = metricData[agent] || []; // Métricas do agent no metrics.json
            const alerts = alertData[agent] || []; // Alertas do agent no alerts.json
            const alertCount = alerts.length; // Número de alertas
            const taskCount = metrics.length; // Número de métricas

            // Ignorar agentes sem métricas e sem alertas
            if (taskCount === 0 && alertCount === 0) return;

            const agentName = agent;

            // Determinar o ícone correspondente com base no número de alertas
            const iconPath = getIconPath(alertCount);

            // Filtrar métricas por tipo de tarefa e incluir alertas
            const cpuMetrics = metrics.filter(item => item.taskType === 0).map(item => item.metrics)
              .concat(alerts.filter(item => item.taskType === 0).map(item => item.metrics));
            const ramMetrics = metrics.filter(item => item.taskType === 1).map(item => item.metrics)
              .concat(alerts.filter(item => item.taskType === 1).map(item => item.metrics));
            const packetLossMetrics = metrics.filter(item => item.taskType === 5).map(item => item.metrics)
              .concat(alerts.filter(item => item.taskType === 5).map(item => item.metrics));
            const latencyMetrics = metrics.filter(item => item.taskType === 2).map(item => item.metrics)
              .concat(alerts.filter(item => item.taskType === 2).map(item => item.metrics));
            const bandwidthMetrics = metrics.filter(item => item.taskType === 3).map(item => item.metrics)
              .concat(alerts.filter(item => item.taskType === 3).map(item => item.metrics));
            const jitterMetrics = metrics.filter(item => item.taskType === 4).map(item => item.metrics)
              .concat(alerts.filter(item => item.taskType === 4).map(item => item.metrics));
            const interfaceMetrics = metrics.filter(item => item.taskType === 6).map(item => item.metrics)
              .concat(alerts.filter(item => item.taskType === 6).map(item => item.metrics));

            // Calcular as médias ou exibir "NA" se não houver dados
            const cpuAvg = calculateAverage(cpuMetrics);
            const ramAvg = calculateAverage(ramMetrics);
            const latencyAvg = calculateAverage(latencyMetrics);
            const packetLossAvg = calculateAverage(packetLossMetrics);
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
                <p><strong>Latency:</strong> ${appendUnit(latencyAvg, 'ms')}</p>
                <p><strong>Packet Loss:</strong> ${appendUnit(packetLossAvg, '%')}</p>
                <p><strong>Bandwidth:</strong> ${appendUnit(bandwidthAvg, 'Mbps')}</p>
                <p><strong>Jitter:</strong> ${appendUnit(jitterAvg, 'ms')}</p>
                <p><strong>Interface:</strong> ${interfaceDisplay}</p>
              </div>
            `;

            // Adicionar o cartão ao container
            container.innerHTML += card;
          });
        }

        // Executar a função para gerar os cartões dinamicamente
        createAgentCards();
      });
  });