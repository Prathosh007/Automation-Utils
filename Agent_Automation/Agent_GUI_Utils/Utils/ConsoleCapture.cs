using System.Text;

namespace GuiAgentUtils.Utils
{
    public class ConsoleCapture : IDisposable
    {
        private readonly TextWriter _originalOut;
        private readonly StringWriter _stringWriter;

        public ConsoleCapture()
        {
            _originalOut = Console.Out;
            _stringWriter = new StringWriter();
            Console.SetOut(_stringWriter);
        }

        public string GetOutput()
        {
            return _stringWriter.ToString();
        }

        public void Dispose()
        {
            Console.SetOut(_originalOut);
            _stringWriter?.Dispose();
        }
    }
}