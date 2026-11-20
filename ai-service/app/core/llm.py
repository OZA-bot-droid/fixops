from langchain_openai import ChatOpenAI
from app.core.config import get_settings


def get_llm():
    settings = get_settings()
    return ChatOpenAI(model=settings.openai_model, api_key=settings.openai_api_key)
