// G.O.A.T API Explorer JavaScript

// Base URL for the API - using relative path for embedded mode
const API_BASE_URL = '/api';

// DOM Elements
const statusIndicator = document.getElementById('status-indicator');
const statusText = document.getElementById('status-text');
const serverDetails = document.getElementById('server-details');
const endpointLinks = document.querySelectorAll('.endpoint');
const sections = document.querySelectorAll('.section');
const executeButtons = document.querySelectorAll('.execute-btn');
const welcomeSection = document.getElementById('welcome-section');

// Initialize the application
document.addEventListener('DOMContentLoaded', () => {
    checkServerStatus();
    setupEndpointNavigation();
    setupExecuteButtons();
});

// Check if the API server is running
async function checkServerStatus() {
    try {
        const response = await fetch(`${API_BASE_URL}/health`, { method: 'GET' });
        
        if (response.ok) {
            statusIndicator.classList.add('online');
            statusText.textContent = 'API Server Online';
            
            // Fetch server info
            fetchServerInfo();
        } else {
            statusIndicator.classList.add('offline');
            statusText.textContent = 'API Server Error';
            serverDetails.textContent = 'Failed to connect to server';
        }
    } catch (error) {
        statusIndicator.classList.add('offline');
        statusText.textContent = 'API Server Offline';
        serverDetails.textContent = 'Cannot connect to API server. Please ensure it is running at ' + API_BASE_URL;
    }
}

// Fetch server information
async function fetchServerInfo() {
    try {
        const response = await fetch(`${API_BASE_URL}/info`, { method: 'GET' });
        
        if (response.ok) {
            const data = await response.json();
            if (data.data) {
                const info = data.data;
                serverDetails.innerHTML = `
                    <div><strong>Name:</strong> ${info.name || 'G.O.A.T API'}</div>
                    <div><strong>Version:</strong> ${info.version || 'N/A'}</div>
                    <div><strong>Java Version:</strong> ${info.javaVersion || 'N/A'}</div>
                    <div><strong>OS:</strong> ${info.osName || 'N/A'}</div>
                    <div><strong>Uptime:</strong> ${formatUptime(info.uptime) || 'N/A'}</div>
                `;
            } else {
                serverDetails.textContent = 'Server info not available';
            }
        } else {
            serverDetails.textContent = 'Failed to fetch server info';
        }
    } catch (error) {
        serverDetails.textContent = 'Error retrieving server info: ' + error.message;
    }
}

// Format uptime from milliseconds to a readable format
function formatUptime(ms) {
    if (!ms) return 'N/A';
    
    const seconds = Math.floor(ms / 1000);
    const minutes = Math.floor(seconds / 60);
    const hours = Math.floor(minutes / 60);
    const days = Math.floor(hours / 24);
    
    return `${days}d ${hours % 24}h ${minutes % 60}m ${seconds % 60}s`;
}

// Set up endpoint navigation
function setupEndpointNavigation() {
    endpointLinks.forEach(link => {
        link.addEventListener('click', () => {
            // Remove active class from all links
            endpointLinks.forEach(l => l.classList.remove('active'));
            
            // Add active class to clicked link
            link.classList.add('active');
            
            // Hide all sections
            sections.forEach(section => section.classList.add('hidden'));
            
            // Show selected section
            const endpoint = link.getAttribute('data-endpoint');
            const targetSection = document.getElementById(`${endpoint}-section`);
            if (targetSection) {
                targetSection.classList.remove('hidden');
            }
        });
    });
}

// Set up execute buttons
function setupExecuteButtons() {
    executeButtons.forEach(button => {
        button.addEventListener('click', async () => {
            const endpoint = button.getAttribute('data-endpoint');
            
            // Get response container
            const responseContainer = document.getElementById(`${endpoint}-response`);
            responseContainer.textContent = 'Loading...';
            
            try {
                let response;
                
                switch (endpoint) {
                    case 'health':
                        response = await executeGet('/health');
                        break;
                    case 'info':
                        response = await executeGet('/info');
                        break;
                    case 'list-testcases':
                        response = await executeGet('/testcases');
                        break;
                    case 'get-testcase':
                        const testcaseId = document.getElementById('testcase-id').value;
                        if (!testcaseId) {
                            responseContainer.textContent = 'Error: Please enter a test case ID';
                            return;
                        }
                        response = await executeGet(`/testcases/${testcaseId}`);
                        break;
                    case 'execute-testcase':
                        const executeId = document.getElementById('execute-testcase-id').value;
                        if (!executeId) {
                            responseContainer.textContent = 'Error: Please enter a test case ID';
                            return;
                        }
                        response = await executePost(`/testcases/${executeId}/execute`);
                        break;
                    case 'validate-testcase':
                        const validateJson = document.getElementById('validate-testcase-json').value;
                        try {
                            // Check if JSON is valid
                            JSON.parse(validateJson);
                            response = await executePost('/testcases/validate', validateJson);
                        } catch (e) {
                            responseContainer.textContent = 'Error: Invalid JSON - ' + e.message;
                            return;
                        }
                        break;
                    case 'list-reports':
                    case 'get-report':
                    case 'download-report':
                        responseContainer.textContent = 'This endpoint is not implemented yet.';
                        return;
                    default:
                        responseContainer.textContent = 'Unknown endpoint';
                        return;
                }
                
                // Format the response
                responseContainer.textContent = JSON.stringify(response, null, 2);
                
            } catch (error) {
                responseContainer.textContent = `Error: ${error.message}`;
            }
        });
    });
}

// Execute GET request
async function executeGet(endpoint) {
    const response = await fetch(`${API_BASE_URL}${endpoint}`);
    
    if (!response.ok) {
        throw new Error(`HTTP error ${response.status}: ${response.statusText}`);
    }
    
    return response.json();
}

// Execute POST request
async function executePost(endpoint, body = null) {
    const options = {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        }
    };
    
    if (body) {
        options.body = body;
    }
    
    const response = await fetch(`${API_BASE_URL}${endpoint}`, options);
    
    if (!response.ok) {
        throw new Error(`HTTP error ${response.status}: ${response.statusText}`);
    }
    
    return response.json();
}
