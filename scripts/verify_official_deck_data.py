import json
import pathlib
import sys

path = pathlib.Path("app/src/main/assets/official-decks/official-decks-v1.json")
payload = json.loads(path.read_text(encoding="utf-8"))
errors = []
product_ids = set()
variant_ids = set()
for product in payload.get("products", []):
    product_id = product.get("id")
    if not product_id or product_id in product_ids:
        errors.append(f"duplicate or missing product id: {product_id!r}")
    product_ids.add(product_id)
    for variant in product.get("variants", []):
        variant_id = variant.get("id")
        if not variant_id or variant_id in variant_ids:
            errors.append(f"duplicate or missing variant id: {variant_id!r}")
        variant_ids.add(variant_id)
        total = 0
        slots = set()
        for card in variant.get("cards", []):
            passcode = card.get("passcode", "")
            section = card.get("section")
            quantity = card.get("quantity")
            key = (passcode, section, card.get("optionGroupId", ""))
            if not (isinstance(passcode, str) and passcode.isdigit() and len(passcode) == 8):
                errors.append(f"{variant_id}: invalid passcode {passcode!r}")
            if section not in {"MAIN", "EXTRA", "SIDE"}:
                errors.append(f"{variant_id}: invalid section {section!r}")
            if not isinstance(quantity, int) or quantity <= 0:
                errors.append(f"{variant_id}: invalid quantity {quantity!r}")
            if key in slots:
                errors.append(f"{variant_id}: duplicate card slot {key!r}")
            slots.add(key)
            total += quantity if isinstance(quantity, int) else 0
        if total != variant.get("totalCardCount"):
            errors.append(f"{variant_id}: totalCardCount is {variant.get('totalCardCount')}, slots total {total}")

verified_path = pathlib.Path("app/src/main/assets/official-decks/verified-official-deck-recipes-v1.json")
verified = json.loads(verified_path.read_text(encoding="utf-8"))
verified_ids = set()
for recipe in verified.get("recipes", []):
    variant = recipe.get("variant", {})
    variant_id = variant.get("id")
    if not variant_id or variant_id in verified_ids:
        errors.append(f"duplicate or missing verified recipe id: {variant_id!r}")
    verified_ids.add(variant_id)
    fixed_count = 0
    set_codes = set()
    for item in recipe.get("baseRanges", []):
        prefix, start, end = item.get("prefix", ""), item.get("start"), item.get("end")
        if not prefix or not isinstance(start, int) or not isinstance(end, int) or start < 1 or end < start:
            errors.append(f"{variant_id}: invalid verified range {item!r}")
            continue
        fixed_count += end - start + 1
        set_codes.update(f"{prefix}{number:03d}" for number in range(start, end + 1))
    for set_code, quantity in recipe.get("quantityOverrides", {}).items():
        if set_code not in set_codes or not isinstance(quantity, int) or quantity < 1:
            errors.append(f"{variant_id}: invalid quantity override {set_code!r}: {quantity!r}")
        else:
            fixed_count += quantity - 1
    if fixed_count != variant.get("fixedCardCount"):
        errors.append(f"{variant_id}: fixedCardCount is {variant.get('fixedCardCount')}, ranges total {fixed_count}")
    groups = recipe.get("bonusGroups", [])
    for group in groups:
        candidates = group.get("candidates", [])
        if not group.get("id") or not candidates or any(candidate.get("setCode") not in set_codes for candidate in candidates):
            errors.append(f"{variant_id}: invalid optional bonus group {group!r}")
    if fixed_count + len(groups) != variant.get("totalCardCount"):
        errors.append(f"{variant_id}: totalCardCount does not equal fixed cards plus optional bonus groups")
if errors:
    print("Official deck data validation failed:", *errors, sep="\n- ", file=sys.stderr)
    sys.exit(1)
print(f"Official deck data valid: {len(product_ids)} products, {len(variant_ids)} selectable variants")