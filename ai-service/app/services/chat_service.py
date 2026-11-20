from app.agent.orchestrator import run_agent

async def run_chat(message: str) -> str:
    answer = await run_agent(message)
    return answer
