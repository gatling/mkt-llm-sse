// static/js/chat.js
document.addEventListener('DOMContentLoaded', function() {
    const chatMessages = document.getElementById('chat-messages');
    const userInput = document.getElementById('user-input');
    const sendButton = document.getElementById('send-button');
    const contextContent = document.getElementById('context-content');
    const buttonText = sendButton.querySelector('.button-text');
    const buttonLoader = sendButton.querySelector('.button-loader');

    // Generate unique session ID
    const sessionId = 'session_' + Date.now();

    function updateMetrics(metrics) {
        if (metrics) {
            document.getElementById('total-time').textContent = 
                `${(metrics.total_time).toFixed(2)}s`;
            if (metrics.embedding_time) {
                document.getElementById('embedding-time').textContent = 
                    `${(metrics.embedding_time).toFixed(2)}s`;
            }
            if (metrics.search_time) {
                document.getElementById('search-time').textContent = 
                    `${(metrics.search_time).toFixed(2)}s`;
            }
            if (metrics.llm_time) {
                document.getElementById('llm-time').textContent = 
                    `${(metrics.llm_time).toFixed(2)}s`;
            }
        }
    }

    function addMessage(text, sender) {
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${sender}`;
        
        if (sender === 'bot') {
            // Convert newlines to paragraphs for bot messages
            const paragraphs = text.split('\n\n')
                .filter(p => p.trim())
                .map(p => `<p>${p.trim().replace(/\n/g, '<br>')}</p>`)
                .join('');
            messageDiv.innerHTML = paragraphs || text;
        } else {
            messageDiv.textContent = text;  // Keep user messages as plain text
        }
        
        chatMessages.appendChild(messageDiv);
        chatMessages.scrollTop = chatMessages.scrollHeight;
        return messageDiv;
    }

    function updateContext(context) {
        contextContent.innerHTML = '';
        if (Array.isArray(context)) {
            context.forEach(text => {
                const p = document.createElement('p');
                p.textContent = text;
                p.style.marginBottom = '0.5rem';
                contextContent.appendChild(p);
            });
        }
    }

    function setLoading(isLoading) {
        sendButton.disabled = isLoading;
        userInput.disabled = isLoading;
        buttonText.style.display = isLoading ? 'none' : 'block';
        buttonLoader.style.display = isLoading ? 'block' : 'none';
    }

    // Custom EventSource class that supports POST
    class EventSourceWithPost {
        constructor(url, options = {}) {
            this.url = url;
            this.options = options;
            this.eventSource = null;
            this.listeners = {
                message: [],
                error: []
            };
            this.connect();
        }

        connect() {
            const headers = new Headers(this.options.headers || {});
            
            fetch(this.url, {
                method: 'POST',
                headers: headers
            }).then(response => {
                const reader = response.body.getReader();
                const decoder = new TextDecoder();
                let buffer = '';

                const processText = (text) => {
                    buffer += text;
                    const lines = buffer.split('\n');
                    buffer = lines.pop(); // Keep the last incomplete line in the buffer

                    for (const line of lines) {
                        if (line.startsWith('data: ')) {
                            const data = line.slice(6).trim();
                            if (data) {  // Only process if data is not empty
                                this.listeners.message.forEach(listener => {
                                    listener({ data });
                                });
                            }
                        }
                    }
                };

                const pump = async () => {
                    try {
                        while (true) {
                            const { value, done } = await reader.read();
                            if (done) break;
                            processText(decoder.decode(value, { stream: true }));
                        }
                    } catch (error) {
                        this.listeners.error.forEach(listener => listener(error));
                    }
                };

                pump();
            }).catch(error => {
                this.listeners.error.forEach(listener => listener(error));
            });
        }

        addEventListener(type, callback) {
            if (this.listeners[type]) {
                this.listeners[type].push(callback);
            }
        }

        set onmessage(callback) {
            this.listeners.message = [callback];
        }

        set onerror(callback) {
            this.listeners.error = [callback];
        }

        close() {
            // Clean up listeners
            this.listeners = {
                message: [],
                error: []
            };
        }
    }

    async function sendMessage() {
        const message = userInput.value.trim();
        if (!message) return;

        // Add user message and clear input
        addMessage(message, 'user');
        userInput.value = '';
        setLoading(true);

        // Create bot message container
        const botMessageDiv = addMessage('', 'bot');
        let currentResponse = '';

        try {
            // Create EventSource with POST method using fetch
            const eventSource = new EventSourceWithPost('/chat', {
                headers: {
                    'X-Message': encodeURIComponent(message),
                    'X-Session-Id': sessionId
                }
            });
            
            eventSource.onmessage = function(event) {
                try {
                    // Handle [DONE] message first
                    if (event.data.trim() === '[DONE]') {
                        eventSource.close();
                        setLoading(false);
                        return;
                    }

                    // Parse JSON for other messages
                    const data = JSON.parse(event.data);
                    
                    if (data.content) {
                            currentResponse += data.content;
                            const paragraphs = currentResponse
                                .split('\n\n')
                                .filter(p => p.trim())
                                .map(p => `<p>${p.trim().replace(/\n/g, '<br>')}</p>`)
                                .join('');
                            
                            botMessageDiv.innerHTML = paragraphs || currentResponse;
                            chatMessages.scrollTop = chatMessages.scrollHeight;
                        
                    }
                    else if (data.metrics) {
                        updateMetrics(data.metrics);
                        if (data.metrics.context) {
                            updateContext(data.metrics.context);
                        }
                    }
                    else if (data.error) {
                        botMessageDiv.textContent = "Sorry, an error occurred. Could you rephrase your question?";
                        console.error('Error:', data.error);
                    }
                } catch (error) {
                    console.error('Error parsing SSE data:', error);
                    // Don't throw error for [DONE] message parse failure
                    if (event.data.trim() !== '[DONE]') {
                        console.error('Data that failed to parse:', event.data);
                    }
                }
            };

            eventSource.onerror = function(error) {
                console.error('SSE Error:', error);
                eventSource.close();
                setLoading(false);
                if (!currentResponse) {
                    botMessageDiv.textContent = "Sorry, an error occurred. Please try again.";
                }
            };

        } catch (error) {
            console.error('Error:', error);
            botMessageDiv.textContent = "Sorry, an error occurred. Please try again.";
            setLoading(false);
        }
    }

    // Event Listeners
    sendButton.addEventListener('click', sendMessage);

    userInput.addEventListener('keypress', function(e) {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });

    // Welcome message
    addMessage("Hello! I'm your assistant. How can I help you?", 'bot');
});