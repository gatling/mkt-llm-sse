from fastapi import FastAPI, Request
from fastapi.templating import Jinja2Templates
from fastapi.staticfiles import StaticFiles
from fastapi.responses import HTMLResponse, StreamingResponse
from pydantic import BaseModel
from typing import List, Optional
import time
import os
import httpx
import json
import asyncio
from dotenv import load_dotenv
from qdrant_client import QdrantClient
from sentence_transformers import SentenceTransformer
from ollama import AsyncClient


load_dotenv()

app = FastAPI()
@app.middleware("http")
async def force_https(request: Request, call_next):
    request.scope["scheme"] = "https"
    response = await call_next(request)
    return response

# Force HTTPS in the request

# Templates and static files configuration
templates = Jinja2Templates(directory="templates")
app.mount("/static", StaticFiles(directory="static"), name="static")

# Configuration
OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
QDRANT_HOST = os.getenv("QDRANT_HOST", "localhost")
QDRANT_KEY = os.getenv("QDRANT_API", "6333")
MODEL_NAME = os.getenv("MODEL_NAME", "phi3")
OLLAMA_CLIENT = AsyncClient(host=OLLAMA_BASE_URL)

# Client initializations
qdrant_client = QdrantClient(url=QDRANT_HOST, api_key=QDRANT_KEY)
embedding_model = SentenceTransformer('paraphrase-MiniLM-L3-v2')

class ChatMessage(BaseModel):
    text: str
    session_id: Optional[str] = None

SYSTEM_PROMPT = """You are Sarah, the customer service assistant for TechStore, an e-commerce store specializing in high-tech products. 
Your goal is to help customers with their questions about orders, products, and customer service.

Important instructions:
1. Always base your responses on the provided context
2. For technical problems:
    - Start with simple solutions (restart, check cables...)
    - Offer technical assistance if needed
    - Provide clear, numbered steps
3. For returns/refunds:
    - First check the 14-day deadline
    - Clearly explain the procedure
    - Ask for the order number
4. For order tracking:
    - Ask for the order number
    - Provide estimated delivery times
    - Offer email tracking
5. Important policies:
    - 2-year warranty on all products
    - 14-day return without justification
    - Defective product exchange within 30 days
6. Support contact:
    - Email: support@techstore.com
    - Phone: 01 23 45 67 89
    - For complex cases only

Response style:
- Be professional but warm
- Give clear and structured responses
- Always offer a concrete solution
- Message must be precise and short
"""

async def get_ollama_stream(prompt: str):
    """Function to call Ollama API in streaming mode"""
    try:
        stream = await OLLAMA_CLIENT.chat(
            model=MODEL_NAME,
            messages=[{
                'role': 'user',
                'content': SYSTEM_PROMPT + "\n\n" + prompt,
            }],
            stream=True
        )
        
        async for chunk in stream:
            if 'message' in chunk and 'content' in chunk['message']:
                yield chunk['message']['content']
    except Exception as e:
        print(f"Ollama API Error: {str(e)}")
        raise

async def stream_response(message: ChatMessage):
    """Generator for SSE streaming"""
    start_time = time.time()
    
    try:
        # 1. Create embedding
        embed_start = time.time()
        question_embedding = embedding_model.encode(message.text)
        embed_time = time.time() - embed_start

        # 2. Search in Qdrant
        search_start = time.time()
        search_results = qdrant_client.search(
            collection_name="documents",
            query_vector=question_embedding.tolist(),
            limit=3
        )
        search_time = time.time() - search_start

        # Extract context
        context = [hit.payload.get("text") for hit in search_results]
        context_text = "\n".join(context)

        # 3. Prepare prompt
        prompt = f"""As TechStore's customer service assistant, help the customer with their request.
        
Relevant context:
{context_text}

Customer question: {message.text}

Respond professionally following the instructions and based on the provided context."""

        # 4. Stream the response
        llm_start = time.time()
        async for content in get_ollama_stream(prompt):
            yield f"data: {json.dumps({'content': content})}\n\n"
            await asyncio.sleep(0)  # Allow other tasks to run
        llm_time = time.time() - llm_start

        # 5. Send metrics at the end
        metrics = {
            'total_time': time.time() - start_time,
            'embedding_time': embed_time,
            'search_time': search_time,
            'llm_time': llm_time,
            'context': context
        }
        yield f"data: {json.dumps({'metrics': metrics})}\n\n"
        yield "data: [DONE]\n\n"

    except Exception as e:
        yield f"data: {json.dumps({'error': str(e)})}\n\n"

@app.get("/", response_class=HTMLResponse)
async def chat_interface(request: Request):
    return templates.TemplateResponse("chat.html", {"request": request})

@app.post("/chat")
async def chat_endpoint(request: Request):
    text = request.headers.get("X-Message")
    session_id = request.headers.get("X-Session-Id")
    
    if not text:
        return {"error": "No message provided"}
        
    message = ChatMessage(text=text, session_id=session_id)
    return StreamingResponse(
        stream_response(message),
        media_type="text/event-stream"
    )

@app.get("/health")
async def health_check():
    return {"status": "healthy"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)