using Microsoft.AspNetCore.Hosting;
using Microsoft.Extensions.Hosting;
using Nito.AsyncEx;
using System;
using System.Threading.Tasks;

namespace UbisoftConnectProxy
{
    public class Program
    {
        public static void Main(string[] args)
        {
            if (args.Length != 1)
            {
                Console.WriteLine("Must give the TCP port of the Java server as the single argument");
                return;
            }

            ushort port = Convert.ToUInt16(args[0]);

            // make sure the logic is single-threaded, much easier to comprehend :P
            AsyncContextThread act = new();
            act.Factory.Run(async () =>
            {
                Logic? logic = null;
                try
                {
                    logic = new();

                    AppDomain.CurrentDomain.UnhandledException += (sender, e) => logic.OnError($"Unhandled exception: {e.ExceptionObject}");

                    await logic.RunAsync(port);
                }
                catch (Exception e)
                {
                    if (logic != null)
                    {
                        logic.Quit($"Fatal error: {e}");
                    }
                    else
                    {
                        Console.WriteLine($"Fatal error: {e}");
                        _ = Task.Run(() => Environment.Exit(0));
                    }
                    return;
                }
                // block forever (so that Main doesn't exit. important? idk)
                await new TaskCompletionSource().Task;
            }).Wait();
        }
    }
}
