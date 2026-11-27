import { useCallback, useEffect, useState } from "react";
import { inventoryErrorMessage } from "../inventoryApi";

/** 목록 불러오기 공통: loading / error / data / reload. deps 가 바뀌면 다시 부른다 */
export default function useLoad(loader, deps) {
  const [state, setState] = useState({ loading: true, error: "", data: null });
  const [revision, setRevision] = useState(0);
  const reload = useCallback(() => setRevision((v) => v + 1), []);
  useEffect(() => {
    const controller = new AbortController();
    setState((s) => ({ ...s, loading: true, error: "" }));
    loader(controller.signal)
      .then((data) => !controller.signal.aborted && setState({ loading: false, error: "", data }))
      .catch((e) => !controller.signal.aborted && setState({ loading: false, error: inventoryErrorMessage(e), data: null }));
    return () => controller.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, revision]);
  return { ...state, reload };
}
