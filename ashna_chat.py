#!/usr/bin/env python3

import json
import os
import sys
import uuid
from datetime import datetime, timezone
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

# ============================================================
# Configuration
# ============================================================

API_KEY = "Im1-wdS-_AdaKMdDUOgI_"

BASE_URL = "https://api.ashna.ai/v1/api"
DEFAULT_MODEL = "ashna-x1"
HISTORY_FILE = "ashna_chats.json"
REQUEST_TIMEOUT = 120

# Fallback models from the AshnaAI documentation.
# The program will first try to load the currently available
# models from the live /models endpoint.
FALLBACK_MODELS = [
    "ashna-x1",
    "ashna-diffusion-1",
    "gpt-4o-mini",
    "gpt-4o",
    "gpt-4.1",
    "gpt-4.1-mini",
    "gpt-5",
    "gpt-5-mini",
    "claude-sonnet-4.5",
    "claude-haiku-4.5",
    "gemini-3.5-Flash",
    "gemini-3-Flash-Preview",
    "gemini-2.5-Flash",
    "gemini-2.5-Pro",
    "deepseek-v4-flash",
    "deepseek-v4-pro",
    "glm-5",
    "glm-5.2",
    "grok-4.3",
    "mistral-large-3",
    "llama-4-scout",
]

# ============================================================
# Utility functions
# ============================================================

def now_iso():
    """Return the current UTC time in ISO 8601 format."""
    return datetime.now(timezone.utc).isoformat()

def clear_screen():
    """Clear the terminal screen."""
    os.system("cls" if os.name == "nt" else "clear")

def pause():
    input("\nPress Enter to continue...")

def generate_chat_id():
    """
    Generate a UUID4 chat ID.

UUID4 provides a randomly generated identifier with
    extremely low collision probability.
    """
    return str(uuid.uuid4())

def shorten_text(text, length=60):
    text = " ".join(str(text).split())
    if len(text) <= length:
        return text
    return text[: length - 3] + "..."

# ============================================================
# Local JSON storage
# ============================================================

def load_chats():
    """Load chats from the local JSON file."""
    if not os.path.exists(HISTORY_FILE):
        return {}

try:
        with open(HISTORY_FILE, "r", encoding="utf-8") as file:
            data = json.load(file)

if not isinstance(data, dict):
            return {}

return data

except json.JSONDecodeError:
        print(f"Warning: {HISTORY_FILE} is not valid JSON.")
        print("Starting with an empty chat history.")
        return {}

except OSError as error:
        print(f"Could not read chat history: {error}")
        return {}

def save_chats(chats):
    """Save chats to the local JSON file."""
    temporary_file = HISTORY_FILE + ".tmp"

try:
        with open(temporary_file, "w", encoding="utf-8") as file:
            json.dump(chats, file, indent=2, ensure_ascii=False)

os.replace(temporary_file, HISTORY_FILE)

except OSError as error:
        print(f"Could not save chat history: {error}")

# ============================================================
# AshnaAI API functions
# ============================================================

def check_api_key():
    """Check whether the API key placeholder was replaced."""
    if not API_KEY.strip() or API_KEY == "Im1-wdS-_AdaKMdDUOgI_":
        print("\nPlease open this file and replace:")
        print('API_KEY = "Im1-wdS-_AdaKMdDUOgI_"')
        print("with your actual AshnaAI API key.")
        return False

return True

def api_request(method, endpoint, payload=None):
    """
    Make an HTTP request to the AshnaAI API.

Uses Python's standard library, so no installation is required.
    """
    url = BASE_URL.rstrip("/") + "/" + endpoint.lstrip("/")

headers = {
        "Authorization": f"Bearer {API_KEY}",
        "Content-Type": "application/json",
        "Accept": "application/json",
    }

body = None
    if payload is not None:
        body = json.dumps(payload).encode("utf-8")

request = Request(
        url=url,
        data=body,
        headers=headers,
        method=method.upper(),
    )

try:
        with urlopen(request, timeout=REQUEST_TIMEOUT) as response:
            raw_response = response.read().decode("utf-8")

if not raw_response:
                return {}

return json.loads(raw_response)

except HTTPError as error:
        try:
            error_body = error.read().decode("utf-8")
            error_data = json.loads(error_body)
            message = error_data.get("error", {}).get("message", error_body)
        except Exception:
            message = str(error)

raise RuntimeError(f"API error {error.code}: {message}") from error

except URLError as error:
        raise RuntimeError(f"Connection error: {error.reason}") from error

except TimeoutError as error:
        raise RuntimeError("The request timed out.") from error

except json.JSONDecodeError as error:
        raise RuntimeError("The API returned invalid JSON.") from error

def fetch_models():
    """
    Fetch available models from AshnaAI.

Falls back to the documentation model list if the request fails.
    """
    try:
        response = api_request("GET", "/models")
        model_data = response.get("data", [])

models = []

for item in model_data:
            if isinstance(item, dict) and item.get("id"):
                models.append(item["id"])
            elif isinstance(item, str):
                models.append(item)

models = list(dict.fromkeys(models))

if models:
            return models

print("The API returned no models. Using fallback model list.")

except RuntimeError as error:
        print(f"Could not load live models: {error}")
        print("Using fallback model list from the documentation.")

return FALLBACK_MODELS.copy()

def send_chat_request(model, messages):
    """Send a non-streaming Chat Completions request."""
    payload = {
        "model": model,
        "messages": messages,
        "max_tokens": 2000,
        "temperature": 0.7,
    }

response = api_request("POST", "/chat/completions", payload)

choices = response.get("choices", [])

if not choices:
        raise RuntimeError("The API response did not contain any choices.")

message = choices[0].get("message", {})
    content = message.get("content")

if content is None:
        raise RuntimeError("The API response did not contain assistant content.")

if isinstance(content, list):
        content = "\n".join(
            part.get("text", "")
            for part in content
            if isinstance(part, dict)
        )

return str(content)

# ============================================================
# Model selection
# ============================================================

def show_models(models, selected_model=None):
    """Display available models."""
    print("\nAvailable models:\n")

for index, model in enumerate(models, start=1):
        marker = ""

if model == DEFAULT_MODEL:
            marker = " [Ashna default]"

if model == selected_model:
            marker += " [selected]"

print(f"{index:>3}. {model}{marker}")

print()

def choose_model(models, default_model=DEFAULT_MODEL):
    """
    Ask the user to select a model.

Pressing Enter selects the default Ashna model.
    """
    selected_default = default_model if default_model in models else models[0]

show_models(models, selected_default)

while True:
        choice = input(
            f"Choose a model number, or press Enter for "
            f"[{selected_default}]: "
        ).strip()

if choice == "":
            return selected_default

if choice.isdigit():
            number = int(choice)

if 1 <= number <= len(models):
                return models[number - 1]

print("Invalid selection. Please enter a valid model number.")

# ============================================================
# Chat management
# ============================================================

def create_chat(chats, models):
    """Create a new chat."""
    print("\nCreate a new chat")
    print("-----------------")

model = choose_model(models)

title = input(
        "\nEnter a title for this chat, or press Enter for automatic title: "
    ).strip()

chat_id = generate_chat_id()

if not title:
        title = "New Chat"

chats[chat_id] = {
        "id": chat_id,
        "title": title,
        "model": model,
        "created_at": now_iso(),
        "updated_at": now_iso(),
        "messages": [],
    }

save_chats(chats)

print(f"\nChat created successfully.")
    print(f"Chat ID: {chat_id}")
    print(f"Model: {model}")

return chat_id

def list_history(chats):
    """Display all locally saved chats."""
    if not chats:
        print("\nNo saved chats found.")
        return []

chat_list = sorted(
        chats.values(),
        key=lambda chat: chat.get("updated_at", ""),
        reverse=True,
    )

print("\nChat history")
    print("------------")

for index, chat in enumerate(chat_list, start=1):
        message_count = len(chat.get("messages", []))
        updated_at = chat.get("updated_at", "Unknown")
        title = chat.get("title", "Untitled")
        model = chat.get("model", "Unknown")

print(f"{index:>3}. {title}")
        print(f"     ID: {chat.get('id')}")
        print(f"     Model: {model}")
        print(f"     Messages: {message_count}")
        print(f"     Updated: {updated_at}")
        print()

return chat_list

def select_chat(chats):
    """Let the user select a saved chat."""
    chat_list = list_history(chats)

if not chat_list:
        pause()
        return None

while True:
        choice = input(
            "Enter the chat number to continue, or press Enter to go back: "
        ).strip()

if choice == "":
            return None

if choice.isdigit():
            number = int(choice)

if 1 <= number <= len(chat_list):
                return chat_list[number - 1]["id"]

print("Invalid selection.")

def rename_chat(chats):
    """Rename an existing chat."""
    chat_id = select_chat(chats)

if not chat_id:
        return

old_title = chats[chat_id].get("title", "Untitled")
    new_title = input(f"New title [{old_title}]: ").strip()

if new_title:
        chats[chat_id]["title"] = new_title
        chats[chat_id]["updated_at"] = now_iso()
        save_chats(chats)
        print("Chat renamed successfully.")

pause()

def delete_chat(chats):
    """Delete an existing chat after confirmation."""
    chat_id = select_chat(chats)

if not chat_id:
        return

chat = chats[chat_id]
    title = chat.get("title", "Untitled")

confirmation = input(
        f"Delete '{title}' permanently? Type DELETE to confirm: "
    ).strip()

if confirmation == "DELETE":
        del chats[chat_id]
        save_chats(chats)
        print("Chat deleted successfully.")
    else:
        print("Deletion cancelled.")

pause()

def export_chat(chats):
    """Export one chat to a separate JSON file."""
    chat_id = select_chat(chats)

if not chat_id:
        return

chat = chats[chat_id]
    safe_title = "".join(
        character if character.isalnum() else "_"
        for character in chat.get("title", "chat")
    )

filename = f"{safe_title}_{chat_id[:8]}.json"

try:
        with open(filename, "w", encoding="utf-8") as file:
            json.dump(chat, file, indent=2, ensure_ascii=False)

print(f"Chat exported to: {filename}")

except OSError as error:
        print(f"Could not export chat: {error}")

pause()

# ============================================================
# Interactive chat session
# ============================================================

def print_message(message):
    """Print one message in a readable format."""
    role = message.get("role", "")
    content = message.get("content", "")

if role == "user":
        print(f"\nYou:\n{content}")
    elif role == "assistant":
        print(f"\nAshnaAI:\n{content}")

def chat_session(chats, chat_id):
    """Run an interactive session for one chat."""
    chat = chats[chat_id]
    model = chat.get("model", DEFAULT_MODEL)
    messages = chat.setdefault("messages", [])

clear_screen()

print("=" * 70)
    print(f"Chat: {chat.get('title', 'Untitled')}")
    print(f"Model: {model}")
    print("=" * 70)

if messages:
        for message in messages:
            print_message(message)
    else:
        print("\nThis is a new chat.")

print("\nCommands: /back, /new, /models, /rename, /help, /exit")

while True:
        try:
            user_input = input("\nYou: ").strip()

except EOFError:
            print()
            return "exit"

except KeyboardInterrupt:
            print("\nReturning to the main menu.")
            return "back"

if not user_input:
            continue

command = user_input.lower()

if command in ("/back", "/menu"):
            save_chats(chats)
            return "back"

if command in ("/exit", "/quit"):
            save_chats(chats)
            return "exit"

if command == "/new":
            save_chats(chats)
            return "new"

if command == "/models":
            print(f"\nCurrent model: {model}")
            continue

if command == "/rename":
            new_title = input("New chat title: ").strip()

if new_title:
                chat["title"] = new_title
                chat["updated_at"] = now_iso()
                save_chats(chats)
                print("Chat renamed successfully.")

continue

if command == "/help":
            print("\nAvailable commands:")
            print("  /back    Return to the main menu")
            print("  /new     Save this chat and start a new chat")
            print("  /models  Show the current model")
            print("  /rename  Rename this chat")
            print("  /help    Show this help message")
            print("  /exit    Exit the program")
            continue

user_message = {
            "role": "user",
            "content": user_input,
        }

messages.append(user_message)
        chat["updated_at"] = now_iso()
        save_chats(chats)

print("\nAshnaAI is thinking...\n")

try:
            assistant_text = send_chat_request(model, messages)

except RuntimeError as error:
            print(f"Request failed: {error}")

# Remove the unsent user message so the history does not
            # contain a question that was never successfully processed.
            if messages and messages[-1] == user_message:
                messages.pop()

chat["updated_at"] = now_iso()
            save_chats(chats)
            continue

assistant_message = {
            "role": "assistant",
            "content": assistant_text,
        }

messages.append(assistant_message)
        chat["updated_at"] = now_iso()

# Automatically use the first user message as the title
        # when the chat was initially called "New Chat".
        if chat.get("title") == "New Chat":
            chat["title"] = shorten_text(user_input, 50)

save_chats(chats)

print(f"AshnaAI:\n{assistant_text}")

# ============================================================
# Main menu
# ============================================================

def print_main_menu(chats):
    print("\n" + "=" * 70)
    print("AshnaAI Local Chat Client")
    print("=" * 70)
    print(f"Saved chats: {len(chats)}")
    print(f"History file: {os.path.abspath(HISTORY_FILE)}")
    print()
    print("1. Start a new chat")
    print("2. Continue a previous chat")
    print("3. Show available models")
    print("4. Rename a chat")
    print("5. Delete a chat")
    print("6. Export a chat")
    print("7. Exit")
    print("=" * 70)

def main():
    if not check_api_key():
        return

chats = load_chats()

print("Loading available AshnaAI models...")
    models = fetch_models()

if DEFAULT_MODEL not in models:
        print(
            f"Warning: default model '{DEFAULT_MODEL}' was not returned "
            "by the API. The first available model will be used instead."
        )

while True:
        try:
            clear_screen()
            print_main_menu(chats)

choice = input("Select an option: ").strip()

if choice == "1":
                chat_id = create_chat(chats, models)
                result = chat_session(chats, chat_id)

if result == "exit":
                    break

if result == "new":
                    continue

elif choice == "2":
                chat_id = select_chat(chats)

if chat_id:
                    result = chat_session(chats, chat_id)

if result == "exit":
                        break

if result == "new":
                        chat_id = create_chat(chats, models)
                        result = chat_session(chats, chat_id)

if result == "exit":
                            break

elif choice == "3":
                clear_screen()
                show_models(models)
                pause()

elif choice == "4":
                rename_chat(chats)

elif choice == "5":
                delete_chat(chats)

elif choice == "6":
                export_chat(chats)

elif choice == "7":
                break

else:
                print("Invalid option.")
                pause()

except KeyboardInterrupt:
            print("\n\nExiting safely...")
            break

except EOFError:
            print("\n\nExiting safely...")
            break

save_chats(chats)
    print("\nGoodbye!")

if __name__ == "__main__":
    main()
