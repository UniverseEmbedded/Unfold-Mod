import json
import argparse

def convert_json_to_markdown(input_file, output_file, exclude_thoughts=False):
    # Load JSON data
    with open(input_file, 'r', encoding='utf-8') as f:
        data = json.load(f)

    # Determine message list based on JSON structure
    if 'chunkedPrompt' in data and 'chunks' in data['chunkedPrompt']:
        messages = data['chunkedPrompt']['chunks']
    elif 'messages' in data:
        messages = data['messages']
    else:
        raise ValueError("Unsupported JSON format: cannot find 'chunks' or 'messages'")

    # Map roles to more friendly labels (optional localization)
    role_map = {
        'user': '用户',
        'model': '助手',
        'system': '系统'
    }

    # Write to Markdown
    with open(output_file, 'w', encoding='utf-8') as md:
        for msg in messages:
            # Skip AI thoughts if requested
            if exclude_thoughts and msg.get('isThought', False):
                continue

            role = role_map.get(msg.get('role', ''), msg.get('role', '').capitalize())
            text = msg.get('text', '').strip()
            if not text:
                continue
            md.write(f"#### {role}\n\n")
            md.write(text + "\n\n---\n\n")

    print(f"Converted {len([m for m in messages if not (exclude_thoughts and m.get('isThought', False))])} messages to {output_file}")

def main():
    parser = argparse.ArgumentParser(description="Convert Google AI Studio JSON chat logs to Markdown")
    parser.add_argument("input", help="Path to the input JSON file")
    parser.add_argument("output", help="Path for the output Markdown file")
    parser.add_argument("-e", "--exclude-thoughts", action="store_true",
                        help="Exclude AI thought process messages (isThought)")
    args = parser.parse_args()
    convert_json_to_markdown(args.input, args.output, args.exclude_thoughts)

if __name__ == "__main__":
    main()
