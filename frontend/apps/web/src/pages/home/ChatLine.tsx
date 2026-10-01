// 롤 인게임 채팅 목업 파일
import { motion } from 'motion/react';

interface ChatMessage {
  time: string;
  name: string;
  champion: string;
  text: string;
}

interface ChatLineProps {
  history: ChatMessage;
  typed: ChatMessage;
}

const CHAR_STAGGER_S = 0.07;
const TYPE_START_S = 0.5;

function Prefix({ time, name, champion }: Omit<ChatMessage, 'text'>) {
  return (
    <>
      <span className='text-[#9a9a9a]'>[{time}]</span>{' '}
      <span className='text-[#4fb4ff]'>
        {name} ({champion}):
      </span>{' '}
    </>
  );
}

export default function ChatLine({ history, typed }: ChatLineProps) {
  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ duration: 0.3 }}
      aria-hidden='true'
      className='relative w-[min(30rem,92vw)] bg-linear-to-r from-black/75 via-black/55 to-black/10 px-3 py-2 text-left text-[15px] leading-relaxed [text-shadow:1px_1px_1px_#000] sm:text-[17px]'
    >
      <p className='text-white/45'>
        <Prefix {...history} />
        {history.text}
      </p>
      <p className='text-white'>
        <Prefix {...typed} />
        {Array.from(typed.text).map((char, i) => (
          <motion.span
            key={`${char}-${i}`}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.01, delay: TYPE_START_S + i * CHAR_STAGGER_S }}
          >
            {char}
          </motion.span>
        ))}
        <motion.span
          className='ml-0.5 inline-block h-[1em] w-px translate-y-[0.15em] bg-white'
          animate={{ opacity: [1, 0, 1] }}
          transition={{ duration: 1, repeat: Infinity }}
        />
      </p>
    </motion.div>
  );
}
