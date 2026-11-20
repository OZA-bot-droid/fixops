from langchain_openai import ChatOpenAI
from app.core.config import settings

def get_llm():
    return ChatOpenAI(
        model=settings.openai_model,
        api_key=settings.openai_api_key
    )
