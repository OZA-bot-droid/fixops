from langchain.agents import create_agent
from langchain.mcp import MCPAdapter

from app.core.llm import get_llm
from app.core.config import get_settings


async def run_agent(message: str) -> str:
    settings = get_settings()
    async with MCPAdapter(settings.mcp_server_url) as adapter:
        tools = await adapter.list_tools()
        agent = create_agent(model=get_llm(), tools=tools)
        result = await agent.ainvoke({"messages": [{"role": "user", "content": message}]})
        return result["messages"][-1].text
