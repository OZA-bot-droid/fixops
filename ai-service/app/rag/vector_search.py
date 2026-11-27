import math
from dataclasses import dataclass

# Python에서 객체가 갖춰야 할 메서드와 속성의 형태를 정의하는 데 사용하는 기능
from typing import Protocol

from app.rag.schemas import DocumentChunk


# 임베딩 모델의 인터페이스 정의
class EmbeddingModel(Protocol):

    # 구조적 타이핑(Structural Typing) : 클래스가 어떤 클래스를 상속했는지가 아니라, 필요한 메서드와 속성을 가지고 있는지를 기준으로 타입 호환성을 판단하는 방식
    # 메서드명, 파라미터의 개수와 이름 및 호출 방식등이 호환되어야 함, 파라미터와 반환 타입이 호환되어야 함
    # 파라미터 타입 -> 반공변성(Contravariance): Protocol에서 요구하는 타입과 같거나 더 넓은 타입을 받을 수 있어함
    # 반환 타입 -> 공변성(Covariance): Protocol에서 요구하는 타입과 같거나 더 구체적인 타입을 반환할 수 있어함

    # ... : Python에서 정식으로 지원하는 Ellipsis(줄임표) 객체
    # Protocol에서 ...은 메서드의 구현을 작성하지 않았음을 나타내는 관례
    def embed_query(self, text: str) -> list[float]: ...

    def embed_documents(
        self,
        texts: list[str],
    ) -> list[list[float]]: ...


# @dataclass : 데이터를 담는 클래스를 편리하게 정의하도록 도와주는 Python 기능, __init__() 등을 자동 생성
# Chunk와 임베딩을 묶어서 보관
@dataclass
class IndexedChunk:
    # 원본 문서 Chunk 객체
    chunk: DocumentChunk
    # 해당 Chunk의 임베딩 벡터
    embedding: list[float]


# 문서 Chunk와 유사도 점수를 묶는 데이터 클래스
@dataclass
class SearchResult:
    chunk: DocumentChunk
    score: float


# 두 벡터의 코사인 유사도 계산
def cosine_similarity(
    vector_a: list[float],
    vector_b: list[float],
) -> float:

    # 벡터 검증
    # 벡터 존재 여부와 차원이 같은지 확인
    if not vector_a or len(vector_a) != len(vector_b):
        raise ValueError("Invalid embedding dimensions")

    # all() : 반복 가능한 객체의 모든 원소가 참(True)으로 평가될 때만 True를 반환
    if not all(
        # 원래 백터에 무한한 값이나 NaN이 포함되어 있는지 확인
        # math.isfinite() : 숫자가 유한한 값인지 확인하는 함수, 값이 무한하거나 NaN이면 True
        math.isfinite(value)
        for value in vector_a + vector_b
    ):
        raise ValueError("Embedding contains non-finite values")

    # 내적 계산
    # zip() : 두 리스트에서 같은 위치의 원소를 묶어주는 함수
    # 내부의 두 값을 묶어 튜플이 됨 => [(,), (,)]
    # strict=True를 사용하면 길이가 다른 리스트를 조용히 잘라서 처리하지 않고 오류를 발생시킴
    dot_product = sum(
        a * b
        # 언패킹(Unpacking) : 여러 값을 각각의 변수에 나누어 할당하는 문법
        for a, b in zip(vector_a, vector_b, strict=True)
    )

    # math.sqrt() : 제곱근 계산
    magnitude_a = math.sqrt(
        sum(value**2 for value in vector_a)
    )
    magnitude_b = math.sqrt(
        sum(value**2 for value in vector_b)
    )

    if magnitude_a == 0 or magnitude_b == 0:
        raise ValueError("Zero vectors are not supported")

    return dot_product / (magnitude_a * magnitude_b)


# Chunk를 임베딩으로 변환하고 Python 메모리에서 관리할 목록을 만드는 역할
def build_vector_index(
    chunks: list[DocumentChunk],
    embedding_model: EmbeddingModel,
) -> list[IndexedChunk]:
    if not chunks:
        return []

    vectors = embedding_model.embed_documents(
        [chunk.content for chunk in chunks]
    )

    if len(vectors) != len(chunks):
        raise ValueError("Embedding count mismatch")

    indexed_chunks = []

    for chunk, vector in zip(chunks, vectors, strict=True):
        # 벡터 유효성 검증
        cosine_similarity(vector, vector)

        indexed_chunks.append(
            IndexedChunk(
                chunk=chunk,
                embedding=vector,
            )
        )

    return indexed_chunks


# 유사도가 높은 결과를 반환
# *은 뒤부터는 키워드 인수로 전달해야 한다는 의미
def search_similar_chunks(
    *,
    query: str,
    indexed_chunks: list[IndexedChunk],
    embedding_model: EmbeddingModel,
    # 가장 유사한 결과 3 개를 반환한다는 의미
    top_k: int = 3,
) -> list[SearchResult]:
    if isinstance(top_k, bool) or top_k <= 0:
        raise ValueError("top_k must be positive")

    if not query.strip():
        raise ValueError("query must not be empty")

    if not indexed_chunks:
        return []

    # 질문을 벡터로 변환
    query_vector = embedding_model.embed_query(query)

    # 각 Chunk의 임베딩과 질문 임베딩을 비교
    results = [
        SearchResult(
            chunk=item.chunk,
            score=cosine_similarity(
                query_vector,
                item.embedding,
            ),
        )
        for item in indexed_chunks
    ]

    # 내림차순 정렬
    # key : 기준값, reverse=True : 내림차순 정렬
    results.sort(
        # lambda : 이름 없는 간단한 함수
        key=lambda result: result.score,
        reverse=True,
    )

    # 슬라이싱 문법, 지정한 상위 top_k개 반환
    return results[:top_k]
