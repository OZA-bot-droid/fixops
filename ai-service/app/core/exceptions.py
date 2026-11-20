"""사용자 정의 예외(Custom Exception)"""

# Exception : Python 기본 예외 클래스
class MCPConnectionError(Exception):
    # 클래스의 목적을 설명하는 docstring
    """MCP server connection error"""

class AIProviderUnavailableError(Exception):
    """LLM provider connection or response error"""