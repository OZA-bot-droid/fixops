import { test } from "node:test";
import assert from "node:assert/strict";
import { locationForest } from "./locationRules.js";

const item = (id, parentId, locationType, code) => ({ id, parentId, locationType, code, name: code });
test("sorts registered buildings and units naturally while keeping common areas last", () => {
  const items = [
    item(1,null,"COMPLEX","C"),
    item(2,1,"BUILDING","10"),
    item(3,1,"BUILDING","2"),
    item(4,1,"BUILDING","1"),
    item(5,3,"UNIT","1001"),
    item(6,3,"UNIT","201"),
    item(7,3,"UNIT","101"),
    item(8,3,"COMMON_AREA","1"),
    item(9,1,"COMMON_AREA","0"),
    item(10,3,"UNIT","102")
  ];
  const [root] = locationForest(items);
  assert.deepEqual(root.children.map(x=>x.id),[4,3,2,9]);
  assert.deepEqual(root.children[1].children.map(x=>x.id),[7,10,6,5,8]);
  assert.deepEqual(items.map(x=>x.id),[1,2,3,4,5,6,7,8,9,10]);
});
test("preserves factory sibling ordering and handles orphan and cyclic nodes", () => {
  const [factory,...rest] = locationForest([
    item(1,null,"FACTORY","F"),item(2,1,"AREA","10"),item(3,1,"AREA","2"),
    item(4,999,"UNIT","101"),item(5,6,"BUILDING","3"),item(6,5,"BUILDING","2")
  ]);
  // Orphans/cycles may be sorted with other root nodes; find the factory explicitly.
  const root = [factory,...rest].find(x=>x.id===1);
  assert.deepEqual(root.children.map(x=>x.id),[2,3]);
  assert.equal([factory,...rest].length,4);
});
