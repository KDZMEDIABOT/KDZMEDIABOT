# tgbich.py

from abstractbich import BichBot
from helpers import get_pretty_json_string, shell, LOG_TRACE

import os
import asyncio
from aiogram import Bot, Dispatcher, types


class TgBich(BichBot):
    def __init__(self, settings_key, connection_settings: dict, config):
        super(TgBich, self).__init__(settings_key, connection_settings, config)
        self.BOT_TOKEN = self.connection_settings('BOT_TOKEN')
        print(f"{self}: entering asyncio.run(self.main())");
        asyncio.run(self.main())
        print(f"{self}: completed asyncio.run(self.main())");


    def needs_irc_markup(self):
        return False

    async def on_message(self, message: types.Message):
        text = message.text
        ticker_str = str(text).strip().upper()
        syms = ticker_str.split("/");
        if len(syms)==0:
            reply_str = "Error. Send `symbol` or `symbol/symbol`"
        else:
            if len(syms)==1: syms.append("USD");
            reply_str = self.compose_ticker_price_reply(syms[0], syms[1])
        await message.answer(
            reply_str,
            parse_mode=types.ParseMode.HTML,
        )

    async def cmd_start_handler(self, event: types.Message):
        await event.answer(
            f"""Hello, {event.from_user.get_mention(as_html=True)} 👋!

<b>Help:</b>

/m /markets - see the markets report (курсы валют, индикаторы и прочее);
/h /help - see the help and welcome message.
/c /calc formula - might use 'price' '(' &ltcrypto&gt; '/' &lt;cryptofiat&gt; ')' via Coinmarketcap

If you send a crypto ticker to the bot, it will query its price at CoinMarketCap.com and report it to you.

<b>Bot author:</b> @ConstAlphaRusDev (Гипн aka гном)""",
            parse_mode=types.ParseMode.HTML,
        )


    async def cmd_markets_handler(self, event: types.Message):
        markets_report_str = self.compose_markets_report()
        print(f"{self}: markets_report_str '{markets_report_str}'");
        await event.answer(
            markets_report_str,
            parse_mode=types.ParseMode.HTML,
        )


    async def cmd_calc_handler(self, message: types.Message):
        text = message.text
        input = str(text).lower()
        retval=self.calc(input)
        reply_str = retval
        await message.answer(
            reply_str,
            parse_mode=types.ParseMode.HTML,
        )
    


    async def cmd_ai_handler(self, message: types.Message):
        """Handle /ai command for Telegram."""
        text = message.text
        input_text = str(text).strip()

        # Remove /ai command prefix
        if input_text.startswith('/ai '):
            query = input_text[4:].strip()
        elif input_text.startswith('/ai'):
            query = ""
        else:
            query = input_text

        if not query:
            await message.answer(
                "Usage: /ai <your question>",
                parse_mode=types.ParseMode.HTML,
            )
            return

        # Check if AI handler is available
        if self.ai_handler is None or not self.ai_handler.is_available():
            await message.answer(
                "AI service is not available. Please try again later.",
                parse_mode=types.ParseMode.HTML,
            )
            return

        # Send "thinking" message
        thinking_msg = await message.answer(
            "Thinking... 🤔",
            parse_mode=types.ParseMode.HTML,
        )

        try:
            # Get user info
            user = message.from_user
            user_id = str(user.id) if user else "unknown"
            chat_id = str(message.chat.id) if message.chat else "private"

            # Call AI handler (sync method, run in executor)
            import asyncio
            response, reasoning = await asyncio.get_event_loop().run_in_executor(
                None,
                self.ai_handler.handle_ai_command,
                user_id,
                chat_id,
                'telegram',
                [query]
            )

            # Delete thinking message
            try:
                await self.bot.delete_message(
                    chat_id=message.chat.id,
                    message_id=thinking_msg.message_id
                )
            except:
                pass

            # Send model reasoning (if present) before the answer
            if reasoning:
                await message.answer(
                    f"<b>AI Reasoning:</b> {reasoning}",
                    parse_mode=types.ParseMode.HTML,
                )

            # Send response (Telegram supports longer messages than IRC)
            max_len = 4000
            if len(response) > max_len:
                response = response[:max_len-3] + "..."

            await message.answer(
                f"<b>AI:</b> {response}",
                parse_mode=types.ParseMode.HTML,
            )
        except Exception as e:
            print(f"Error in AI handler: {e}")
            await message.answer(
                f"Error: {str(e)}",
                parse_mode=types.ParseMode.HTML,
            )

    async def cmd_tasks_handler(self, message: types.Message):
        """Handle /tasks command for Telegram."""
        if self.ai_handler is None or not self.ai_handler.is_available() or not self.ai_handler.ws_client:
            await message.answer("AI service is not available.", parse_mode=types.ParseMode.HTML)
            return
        try:
            result = self.ai_handler.ws_client.task_list()
            if result.get("error"):
                err = result.get("error")
                await message.answer(f"<b>Task Manager</b>: Error: {err}", parse_mode=types.ParseMode.HTML)
                return
            tasks = result.get("tasks", [])
            if not tasks:
                await message.answer("<b>Task Manager</b>: No tasks running", parse_mode=types.ParseMode.HTML)
                return
            running = [t for t in tasks if t.get("status") == "running"]
            lines = [f"<b>Task Manager</b>: {len(running)} running, {len(tasks)} total"]
            for t in running[:10]:
                tid = t.get("taskId", "?")
                platform = t.get("platform", "?")
                user = t.get("userId", "?")[:15]
                started = t.get("startedAt", "?")
                lines.append(f"{tid} | {platform} | {user} | {started}")
            if len(running) > 10:
                lines.append(f"...and {len(running) - 10} more tasks")
            await message.answer("\n".join(lines), parse_mode=types.ParseMode.HTML)
        except Exception as e:
            print(f"Error in cmd_tasks_handler: {e}")
            await message.answer(f"<b>Task Manager Error</b>: {str(e)}", parse_mode=types.ParseMode.HTML)

    async def cmd_kill_handler(self, message: types.Message):
        """Handle /kill command for Telegram."""
        text = str(message.text).strip()
        parts = text.split()
        if len(parts) < 2:
            await message.answer("Usage: /kill <task_id> or /kill *", parse_mode=types.ParseMode.HTML)
            return
        task_id = parts[1]
        if self.ai_handler is None or not self.ai_handler.is_available() or not self.ai_handler.ws_client:
            await message.answer("AI service is not available.", parse_mode=types.ParseMode.HTML)
            return
        try:
            result = self.ai_handler.ws_client.task_kill(task_id)
            if result.get("error"):
                err = result.get("error")
                await message.answer(f"<b>Task Manager</b>: Error: {err}", parse_mode=types.ParseMode.HTML)
                return
            msg = result.get("message", "Done")
            await message.answer(f"<b>Task Manager</b>: {msg}", parse_mode=types.ParseMode.HTML)
        except Exception as e:
            print(f"Error in cmd_kill_handler: {e}")
            await message.answer(f"<b>Task Manager Error</b>: {str(e)}", parse_mode=types.ParseMode.HTML)

    async def main(self):
        print(f"{self}: new Bot");
        self.bot = Bot(token=self.BOT_TOKEN)
        print(f"{self}: done new Bot");
        try:
            print(f"{self}: new Dispatcher");
            self.disp = Dispatcher(bot=self.bot)
            print(f"{self}: done new Dispatcher");
            self.disp.register_message_handler(self.cmd_start_handler, commands={"start", "s", "h", "help"})
            self.disp.register_message_handler(self.cmd_markets_handler, commands={"markets", "m"})
            self.disp.register_message_handler(self.cmd_calc_handler, commands={"calc", "c"})
            self.disp.register_message_handler(self.cmd_ai_handler, commands={"ai", "a"})
            self.disp.register_message_handler(self.cmd_tasks_handler, commands={"tasks", "t"})
            self.disp.register_message_handler(self.cmd_kill_handler, commands={"kill", "k"})
            self.disp.register_message_handler(self.on_message)
            print(f"{self}: entering start_polling()");
            await self.disp.start_polling()
            print(f"{self}: completed start_polling()");
        finally:
            print(f"{self}: finally: entering bot.close()");
            await self.bot.close()
            print(f"{self}: finally: completed bot.close()");



def run_tgbich(settings_key, connection_settings: dict, config, section_key):
    connection_props = connection_settings
    print(f'run_tgbich settings_key="{settings_key}"')
    print(f'{settings_key}.parent pid: {os.getppid()}')
    print(f'{settings_key}.pid: {os.getpid()}')
    bot = TgBich(settings_key, connection_settings, config)
    bot.login_and_loop()

